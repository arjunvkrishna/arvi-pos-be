package com.neram.pos.service;

import com.neram.pos.dto.SalaryDTO;
import com.neram.pos.dto.SalaryRequestDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.Salary;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryService {

    private final SalaryRepository salaryRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    public List<SalaryDTO> getAllSalaries() {
        return salaryRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<SalaryDTO> getSalariesByEmployee(Long employeeId) {
        return salaryRepository.findByEmployeeId(employeeId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<SalaryDTO> getSalariesByMonthYear(int month, int year) {
        return salaryRepository.findByMonthAndYear(month, year).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
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
        auditLogService.log("SALARY_PAID", "SALARY", id, null, "Paid salary ₹" + saved.getNetSalary());
        return toDTO(saved);
    }

    @Transactional
    public void deleteSalary(Long id) {
        Salary sal = salaryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Salary not found with id: " + id));
        salaryRepository.delete(sal);
        auditLogService.log("DELETE_SALARY", "SALARY", id, null, "Deleted salary record id " + id);
    }

    public SalaryDTO toDTO(Salary s) {
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
                .paid(s.getPaid())
                .paidDate(s.getPaidDate())
                .paymentMode(s.getPaymentMode())
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
