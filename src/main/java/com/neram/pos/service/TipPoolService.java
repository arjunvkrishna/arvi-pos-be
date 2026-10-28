package com.neram.pos.service;

import com.neram.pos.dto.TipPoolCalculateRequestDTO;
import com.neram.pos.dto.TipPoolDistributionDTO;
import com.neram.pos.dto.TipPoolEmployeeShareDTO;
import com.neram.pos.entity.Attendance;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.TipPoolDistribution;
import com.neram.pos.entity.TipPoolEmployeeShare;
import com.neram.pos.repository.AttendanceRepository;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.TipPoolDistributionRepository;
import com.neram.pos.repository.TipPoolEmployeeShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TipPoolService {

    private final TipPoolDistributionRepository tipPoolDistributionRepository;
    private final TipPoolEmployeeShareRepository tipPoolEmployeeShareRepository;
    private final BillRepository billRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    /** The branch the caller works in (or picked); null when an owner or main manager looks at the whole cafe. */
    private static Long currentOutlet() {
        Long outlet = com.neram.pos.access.OutletContext.currentIdOrNull();
        return outlet != null && outlet != com.neram.pos.access.OutletContext.NO_OUTLET ? outlet : null;
    }

    public static LocalDate currentBusinessDate() {
        return com.neram.pos.util.BusinessDate.now();
    }

    @Transactional(readOnly = true)
    public Optional<TipPoolDistributionDTO> getDistribution(LocalDate date) {
        LocalDate queryDate = date != null ? date : currentBusinessDate();
        return tipPoolDistributionRepository.findByDistributionDateWithShares(queryDate, currentOutlet())
                .map(this::toDTO);
    }

    @Transactional
    public TipPoolDistributionDTO calculateAndDistribute(TipPoolCalculateRequestDTO request, Long userId) {
        LocalDate distDate = (request != null && request.getDistributionDate() != null)
                ? request.getDistributionDate()
                : currentBusinessDate();

        // 1. Calculate or override total tips
        BigDecimal totalTips = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (request != null && request.getOverrideTotalTips() != null && request.getOverrideTotalTips().compareTo(BigDecimal.ZERO) >= 0) {
            totalTips = request.getOverrideTotalTips().setScale(2, RoundingMode.HALF_UP);
        } else {
            List<Bill> paidBills = billRepository.findPaidByBusinessDate(distDate);
            for (Bill bill : paidBills) {
                if (bill.getDeletedAt() == null && bill.getTipAmount() != null && bill.getTipAmount().compareTo(BigDecimal.ZERO) > 0) {
                    totalTips = totalTips.add(bill.getTipAmount());
                }
            }
        }

        // 2. Determine hours worked per employee
        Map<Long, BigDecimal> employeeHours = new LinkedHashMap<>();
        if (request != null && request.getCustomHours() != null && !request.getCustomHours().isEmpty()) {
            for (TipPoolCalculateRequestDTO.EmployeeHoursInput input : request.getCustomHours()) {
                if (input.getEmployeeId() != null && input.getHoursWorked() != null && input.getHoursWorked().compareTo(BigDecimal.ZERO) > 0) {
                    employeeHours.put(input.getEmployeeId(), input.getHoursWorked().setScale(2, RoundingMode.HALF_UP));
                }
            }
        } else {
            List<Attendance> attendances = attendanceRepository.findByDate(distDate);
            for (Attendance att : attendances) {
                if (att.getStatus() == Attendance.Status.PRESENT) {
                    double hours = 8.0;
                    if (att.getCheckIn() != null && att.getCheckOut() != null) {
                        long minutes = Duration.between(att.getCheckIn(), att.getCheckOut()).toMinutes();
                        if (minutes > 0) {
                            hours = minutes / 60.0;
                        }
                    }
                    employeeHours.merge(att.getEmployeeId(), BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
                } else if (att.getStatus() == Attendance.Status.HALF_DAY) {
                    employeeHours.merge(att.getEmployeeId(), BigDecimal.valueOf(4.0).setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
                }
            }
            if (employeeHours.isEmpty()) {
                // Fallback to active employees with 8 hours each
                List<Employee> activeEmps = employeeRepository.findByActiveTrue();
                for (Employee emp : activeEmps) {
                    employeeHours.put(emp.getId(), BigDecimal.valueOf(8.0).setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

        BigDecimal totalHours = employeeHours.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (totalHours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Cannot distribute tips: total hours worked is 0.");
        }

        // 3. Rate per hour
        BigDecimal ratePerHour = totalTips.divide(totalHours, 4, RoundingMode.HALF_UP);

        // 4. Calculate individual shares
        List<TipPoolEmployeeShare> shareEntities = new ArrayList<>();
        BigDecimal sumShares = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        TipPoolEmployeeShare topShare = null;
        BigDecimal maxHours = BigDecimal.ZERO;

        // Sort employees by hours descending for clean residual allocation
        List<Map.Entry<Long, BigDecimal>> sortedEntries = new ArrayList<>(employeeHours.entrySet());
        sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<Long, BigDecimal> entry : sortedEntries) {
            Long empId = entry.getKey();
            BigDecimal hours = entry.getValue();
            Employee employee = employeeRepository.findById(empId)
                    .orElseThrow(() -> new IllegalArgumentException("Employee not found with id: " + empId));

            BigDecimal shareAmt = ratePerHour.multiply(hours).setScale(2, RoundingMode.HALF_UP);
            sumShares = sumShares.add(shareAmt);

            TipPoolEmployeeShare share = TipPoolEmployeeShare.builder()
                    .employee(employee)
                    .hoursWorked(hours)
                    .tipAmount(shareAmt)
                    .paid(false)
                    .build();

            shareEntities.add(share);

            if (topShare == null || hours.compareTo(maxHours) > 0) {
                topShare = share;
                maxHours = hours;
            }
        }

        // 5. Allocate residual paise to top earner so sum == totalTips exactly
        BigDecimal residual = totalTips.subtract(sumShares).setScale(2, RoundingMode.HALF_UP);
        if (residual.compareTo(BigDecimal.ZERO) != 0 && topShare != null) {
            topShare.setTipAmount(topShare.getTipAmount().add(residual).setScale(2, RoundingMode.HALF_UP));
        }

        // 6. Save or update distribution
        TipPoolDistribution distribution = tipPoolDistributionRepository.findByDistributionDate(distDate, currentOutlet())
                .orElse(TipPoolDistribution.builder()
                        .distributionDate(distDate)
                        .outletId(currentOutlet())
                        .createdBy(userId)
                        .createdAt(Instant.now())
                        .build());

        distribution.setTotalTips(totalTips);
        distribution.setTotalHours(totalHours);
        distribution.setRatePerHour(ratePerHour);

        // A payout already made cannot be silently wiped by recalculating the day
        if (distribution.getShares() != null && distribution.getShares().stream().anyMatch(sh -> Boolean.TRUE.equals(sh.getPaid()))) {
            throw new IllegalStateException("Tips for " + distDate + " were already paid out; they cannot be recalculated.");
        }

        // Clear previous shares if re-calculating
        if (distribution.getShares() != null) {
            distribution.getShares().clear();
        } else {
            distribution.setShares(new ArrayList<>());
        }

        for (TipPoolEmployeeShare share : shareEntities) {
            share.setDistribution(distribution);
            distribution.getShares().add(share);
        }

        TipPoolDistribution saved = tipPoolDistributionRepository.save(distribution);
        return toDTO(saved);
    }

    @Transactional
    public TipPoolEmployeeShareDTO markSharePaid(Long shareId) {
        TipPoolEmployeeShare share = tipPoolEmployeeShareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Tip share not found with id: " + shareId));
        share.setPaid(true);
        share.setPaidAt(Instant.now());
        return toShareDTO(tipPoolEmployeeShareRepository.save(share));
    }

    public TipPoolDistributionDTO toDTO(TipPoolDistribution d) {
        List<TipPoolEmployeeShareDTO> shares = (d.getShares() != null)
                ? d.getShares().stream().map(this::toShareDTO).collect(Collectors.toList())
                : Collections.emptyList();

        return TipPoolDistributionDTO.builder()
                .id(d.getId())
                .distributionDate(d.getDistributionDate())
                .totalTips(d.getTotalTips())
                .totalHours(d.getTotalHours())
                .ratePerHour(d.getRatePerHour())
                .createdBy(d.getCreatedBy())
                .createdAt(d.getCreatedAt())
                .shares(shares)
                .build();
    }

    private TipPoolEmployeeShareDTO toShareDTO(TipPoolEmployeeShare s) {
        return TipPoolEmployeeShareDTO.builder()
                .id(s.getId())
                .employeeId(s.getEmployee() != null ? s.getEmployee().getId() : null)
                .employeeName(s.getEmployee() != null ? s.getEmployee().getName() : null)
                .hoursWorked(s.getHoursWorked())
                .tipAmount(s.getTipAmount())
                .paid(s.getPaid())
                .paidAt(s.getPaidAt())
                .build();
    }
}
