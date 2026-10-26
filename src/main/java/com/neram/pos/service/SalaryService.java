package com.neram.pos.service;

import com.neram.pos.dto.SalaryAdvanceDTO;
import com.neram.pos.dto.SalaryAdvanceRequestDTO;
import com.neram.pos.dto.SalaryDTO;
import com.neram.pos.dto.SalaryRequestDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.Salary;
import com.neram.pos.entity.SalaryAdvance;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.SalaryAdvanceRepository;
import com.neram.pos.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryService {

    private final SalaryRepository salaryRepository;
    private final SalaryAdvanceRepository advanceRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    public List<SalaryDTO> getAllSalaries() {
        return toDTOs(salaryRepository.findAll());
    }

    public List<SalaryDTO> getSalariesByEmployee(Long employeeId) {
        return toDTOs(salaryRepository.findByEmployeeId(employeeId));
    }

    private static final org.springframework.data.domain.Sort NEWEST_PAY_FIRST =
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("year"),
                    org.springframework.data.domain.Sort.Order.desc("month"), org.springframework.data.domain.Sort.Order.desc("id"));

    /** One page of salary records, newest pay month first. */
    public org.springframework.data.domain.Page<SalaryDTO> getAllSalaries(org.springframework.data.domain.Pageable pageable) {
        return withAdvances(salaryRepository.findAll(com.neram.pos.util.PagedResponse.withSort(pageable, NEWEST_PAY_FIRST)));
    }

    /** One page of an employee's salary records, newest pay month first. */
    public org.springframework.data.domain.Page<SalaryDTO> getSalariesByEmployee(Long employeeId, org.springframework.data.domain.Pageable pageable) {
        return withAdvances(salaryRepository.findByEmployeeId(employeeId, com.neram.pos.util.PagedResponse.withSort(pageable, NEWEST_PAY_FIRST)));
    }

    private org.springframework.data.domain.Page<SalaryDTO> withAdvances(org.springframework.data.domain.Page<Salary> page) {
        return new org.springframework.data.domain.PageImpl<>(toDTOs(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    public List<SalaryDTO> getSalariesByMonthYear(int month, int year) {
        return toDTOs(salaryRepository.findByMonthAndYear(month, year));
    }

    @Transactional
    public SalaryDTO createOrUpdateSalary(SalaryRequestDTO dto) {
        Employee emp = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + dto.getEmployeeId()));

        if (dto.getMonth() == null || dto.getMonth() < 1 || dto.getMonth() > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (dto.getYear() == null || dto.getYear() < 2000 || dto.getYear() > 2100) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100");
        }
        if ((dto.getBasic() != null && dto.getBasic().signum() < 0)
                || (dto.getAllowances() != null && dto.getAllowances().signum() < 0)
                || (dto.getDeductions() != null && dto.getDeductions().signum() < 0)) {
            throw new IllegalArgumentException("Basic, allowances and deductions cannot be negative");
        }

        Salary sal = salaryRepository.findByEmployeeIdAndMonthAndYear(dto.getEmployeeId(), dto.getMonth(), dto.getYear())
                .orElse(new Salary());
        if (Boolean.TRUE.equals(sal.getPaid())) {
            throw new IllegalStateException("Salary for " + emp.getName() + " (" + dto.getMonth() + "/" + dto.getYear() + ") is already paid and cannot be changed.");
        }

        sal.setEmployeeId(emp.getId());
        sal.setMonth(dto.getMonth());
        sal.setYear(dto.getYear());
        java.math.BigDecimal basic = dto.getBasic() != null ? dto.getBasic()
                : (emp.getBasicSalary() != null ? java.math.BigDecimal.valueOf(emp.getBasicSalary()).setScale(2, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO);
        sal.setBasic(basic);
        sal.setAllowances(dto.getAllowances() != null ? dto.getAllowances() : java.math.BigDecimal.ZERO);
        sal.setDeductions(dto.getDeductions() != null ? dto.getDeductions() : java.math.BigDecimal.ZERO);

        java.math.BigDecimal net = sal.getBasic().add(sal.getAllowances()).subtract(sal.getDeductions());
        if (net.signum() < 0) {
            throw new IllegalArgumentException("Deductions cannot exceed basic plus allowances");
        }
        sal.setNetSalary(net.setScale(2, java.math.RoundingMode.HALF_UP));
        BigDecimal advanced = sal.getId() == null ? BigDecimal.ZERO : advanceTotal(sal.getId());
        if (sal.getNetSalary().compareTo(advanced) < 0) {
            throw new IllegalArgumentException("Net salary cannot be less than the advance already given (₹" + advanced.toPlainString() + ")");
        }
        sal.setPaid(dto.getPaid() != null ? dto.getPaid() : false);
        sal.setPaidDate(dto.getPaidDate());
        if (dto.getPaymentMode() != null) sal.setPaymentMode(dto.getPaymentMode());
        sal.setNotes(dto.getNotes());
        if (sal.getId() == null) {
            sal.setCreatedAt(LocalDateTime.now());
        }

        Salary saved = salaryRepository.save(sal);
        auditLogService.log("SALARY", "SALARY", saved.getId(), null, "Salary for " + emp.getName() + " (" + dto.getMonth() + "/" + dto.getYear() + ") - ₹" + saved.getNetSalary());
        return toDTO(saved);
    }

    @Transactional
    public SalaryDTO markAsPaid(Long id, String paymentMode) {
        Salary sal = salaryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Salary record not found with id: " + id));
        if (Boolean.TRUE.equals(sal.getPaid())) {
            throw new IllegalStateException("Salary is already marked as paid on " + sal.getPaidDate());
        }
        sal.setPaid(true);
        sal.setPaidDate(LocalDate.now());
        if (paymentMode != null) sal.setPaymentMode(paymentMode);

        Salary saved = salaryRepository.save(sal);
        BigDecimal advanced = advanceTotal(id);
        auditLogService.log("SALARY_PAID", "SALARY", id, null, "Paid salary balance ₹" + saved.getNetSalary().subtract(advanced).toPlainString()
                + (advanced.signum() > 0 ? " (net ₹" + saved.getNetSalary() + " less advance ₹" + advanced.toPlainString() + ")" : ""));
        return toDTO(saved);
    }

    /**
     * Give a staff member part of the month's pay early. Creates the month's salary record (basic from the staff profile) when
     * there is none yet. The advance can never be more than what is still unpaid, and a paid month takes no advance.
     */
    @Transactional
    public SalaryDTO giveAdvance(SalaryAdvanceRequestDTO req, String givenBy) {
        if (req.getEmployeeId() == null) throw new IllegalArgumentException("Choose an employee");
        Employee emp = employeeRepository.findById(req.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + req.getEmployeeId()));
        if (req.getMonth() == null || req.getMonth() < 1 || req.getMonth() > 12) throw new IllegalArgumentException("Month must be between 1 and 12");
        if (req.getYear() == null || req.getYear() < 2000 || req.getYear() > 2100) throw new IllegalArgumentException("Year must be between 2000 and 2100");
        BigDecimal amount = req.getAmount() == null ? BigDecimal.ZERO : req.getAmount().setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Advance must be more than zero");

        Salary sal = salaryRepository.findByEmployeeIdAndMonthAndYear(emp.getId(), req.getMonth(), req.getYear()).orElse(null);
        if (sal == null) {
            sal = new Salary();
            sal.setEmployeeId(emp.getId());
            sal.setMonth(req.getMonth());
            sal.setYear(req.getYear());
            BigDecimal basic = emp.getBasicSalary() != null ? BigDecimal.valueOf(emp.getBasicSalary()).setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
            sal.setBasic(basic);
            sal.setNetSalary(basic);
            sal.setPaid(false);
            sal.setNotes("Monthly payout");
            sal = salaryRepository.save(sal);
        } else if (Boolean.TRUE.equals(sal.getPaid())) {
            throw new IllegalStateException("Salary for " + emp.getName() + " (" + req.getMonth() + "/" + req.getYear() + ") is already paid; no advance can be added.");
        }

        BigDecimal balance = sal.getNetSalary().subtract(advanceTotal(sal.getId()));
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(balance.signum() <= 0
                    ? "No salary left to advance for this month. Set the basic salary first."
                    : "Advance cannot be more than the balance of ₹" + balance.toPlainString());
        }

        SalaryAdvance adv = new SalaryAdvance();
        adv.setSalaryId(sal.getId());
        adv.setAmount(amount);
        adv.setGivenDate(LocalDate.now());
        adv.setGivenBy(givenBy);
        adv.setNote(req.getNote() != null && !req.getNote().isBlank() ? req.getNote().trim() : null);
        adv.setCreatedAt(LocalDateTime.now());
        advanceRepository.save(adv);
        auditLogService.log("SALARY_ADVANCE", "SALARY", sal.getId(), null, "Advance ₹" + amount.toPlainString() + " to " + emp.getName()
                + " for " + req.getMonth() + "/" + req.getYear() + " (balance ₹" + balance.subtract(amount).toPlainString() + ")");
        return toDTO(sal);
    }

    /** Take back an advance entered by mistake (only while the month is not paid). */
    @Transactional
    public SalaryDTO deleteAdvance(Long advanceId) {
        SalaryAdvance adv = advanceRepository.findById(advanceId)
                .orElseThrow(() -> new RuntimeException("Advance not found with id: " + advanceId));
        Salary sal = salaryRepository.findById(adv.getSalaryId())
                .orElseThrow(() -> new RuntimeException("Salary record not found"));
        if (Boolean.TRUE.equals(sal.getPaid())) {
            throw new IllegalStateException("This salary is already paid; the advance cannot be removed.");
        }
        advanceRepository.delete(adv);
        auditLogService.log("SALARY_ADVANCE_REMOVED", "SALARY", sal.getId(), null, "Removed advance ₹" + adv.getAmount().toPlainString());
        return toDTO(sal);
    }

    private BigDecimal advanceTotal(Long salaryId) {
        return advanceRepository.findBySalaryIdOrderByIdAsc(salaryId).stream()
                .map(SalaryAdvance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<SalaryDTO> toDTOs(List<Salary> salaries) {
        List<Long> ids = salaries.stream().map(Salary::getId).collect(Collectors.toList());
        Map<Long, List<SalaryAdvance>> byId = ids.isEmpty() ? Collections.emptyMap()
                : advanceRepository.findBySalaryIdIn(ids).stream().collect(Collectors.groupingBy(SalaryAdvance::getSalaryId));
        return salaries.stream().map(s -> toDTO(s, byId.getOrDefault(s.getId(), Collections.emptyList()))).collect(Collectors.toList());
    }

    @Transactional
    public void deleteSalary(Long id) {
        Salary sal = salaryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Salary not found with id: " + id));
        salaryRepository.delete(sal);
        auditLogService.log("DELETE_SALARY", "SALARY", id, null, "Deleted salary record id " + id);
    }

    public SalaryDTO toDTO(Salary s) {
        return toDTO(s, advanceRepository.findBySalaryIdOrderByIdAsc(s.getId()));
    }

    private SalaryDTO toDTO(Salary s, List<SalaryAdvance> advanceRows) {
        List<SalaryAdvance> rows = new ArrayList<>(advanceRows == null ? Collections.<SalaryAdvance>emptyList() : advanceRows);
        rows.sort(java.util.Comparator.comparing(SalaryAdvance::getId));
        BigDecimal advanced = rows.stream().map(SalaryAdvance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String empName = employeeRepository.findById(s.getEmployeeId())
                .map(Employee::getName)
                .orElse("Unknown Employee");

        return SalaryDTO.builder()
                .id(s.getId())
                .employeeId(s.getEmployeeId())
                .employeeName(empName)
                .month(s.getMonth())
                .year(s.getYear())
                .basic(s.getBasic())
                .allowances(s.getAllowances())
                .deductions(s.getDeductions())
                .netSalary(s.getNetSalary())
                .advance(advanced)
                .balance(s.getNetSalary().subtract(advanced))
                .advances(rows.stream().map(a -> SalaryAdvanceDTO.builder().id(a.getId()).amount(a.getAmount())
                        .givenDate(a.getGivenDate()).givenBy(a.getGivenBy()).note(a.getNote()).build()).collect(Collectors.toList()))
                .paid(s.getPaid())
                .paidDate(s.getPaidDate())
                .paymentMode(s.getPaymentMode())
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
