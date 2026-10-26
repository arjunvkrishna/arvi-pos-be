package com.neram.pos.service;

import com.neram.pos.dto.SalaryAdvanceRequestDTO;
import com.neram.pos.dto.SalaryDTO;
import com.neram.pos.dto.SalaryRequestDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.Salary;
import com.neram.pos.entity.SalaryAdvance;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.SalaryAdvanceRepository;
import com.neram.pos.repository.SalaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaryAdvanceTest {

    @Mock private SalaryRepository salaryRepository;
    @Mock private SalaryAdvanceRepository advanceRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private SalaryService salaryService;

    private final List<SalaryAdvance> stored = new ArrayList<>();
    private Salary salary;

    @BeforeEach
    void setUp() {
        Employee emp = new Employee();
        emp.setId(7L);
        emp.setName("Asha");
        emp.setBasicSalary(20000.0);
        lenient().when(employeeRepository.findById(7L)).thenReturn(Optional.of(emp));

        salary = new Salary();
        salary.setId(11L);
        salary.setEmployeeId(7L);
        salary.setMonth(10);
        salary.setYear(2026);
        salary.setBasic(new BigDecimal("20000.00"));
        salary.setNetSalary(new BigDecimal("20000.00"));
        lenient().when(salaryRepository.findByEmployeeIdAndMonthAndYear(7L, 10, 2026)).thenReturn(Optional.of(salary));
        lenient().when(salaryRepository.findById(11L)).thenReturn(Optional.of(salary));
        lenient().when(salaryRepository.save(any(Salary.class))).thenAnswer(i -> { Salary s = i.getArgument(0); if (s.getId() == null) s.setId(11L); return s; });

        lenient().when(advanceRepository.save(any(SalaryAdvance.class))).thenAnswer(i -> { SalaryAdvance a = i.getArgument(0); a.setId((long) stored.size() + 1); stored.add(a); return a; });
        lenient().when(advanceRepository.findBySalaryIdOrderByIdAsc(11L)).thenAnswer(i -> new ArrayList<>(stored));
        lenient().when(advanceRepository.findById(any())).thenAnswer(i -> stored.stream().filter(a -> a.getId().equals(i.getArgument(0))).findFirst());
    }

    private SalaryAdvanceRequestDTO req(String amount) {
        SalaryAdvanceRequestDTO r = new SalaryAdvanceRequestDTO();
        r.setEmployeeId(7L); r.setMonth(10); r.setYear(2026); r.setAmount(new BigDecimal(amount)); r.setNote("festival");
        return r;
    }

    @Test
    @DisplayName("pay 20000, advance 2000: balance to pay is 18000")
    void balanceAfterAdvance() {
        SalaryDTO dto = salaryService.giveAdvance(req("2000"), "manager");
        assertEquals(0, new BigDecimal("2000.00").compareTo(dto.getAdvance()));
        assertEquals(0, new BigDecimal("18000.00").compareTo(dto.getBalance()));
        assertEquals(1, dto.getAdvances().size());
        assertEquals("manager", dto.getAdvances().get(0).getGivenBy());
    }

    @Test
    @DisplayName("advances add up, never beyond what is left, zero and negative refused")
    void limits() {
        salaryService.giveAdvance(req("2000"), "m");
        salaryService.giveAdvance(req("3000"), "m");
        assertEquals(0, new BigDecimal("15000.00").compareTo(salaryService.toDTO(salary).getBalance()));
        assertThrows(IllegalArgumentException.class, () -> salaryService.giveAdvance(req("15000.01"), "m"));
        assertThrows(IllegalArgumentException.class, () -> salaryService.giveAdvance(req("0"), "m"));
        assertThrows(IllegalArgumentException.class, () -> salaryService.giveAdvance(req("-5"), "m"));
        assertDoesNotThrow(() -> salaryService.giveAdvance(req("15000"), "m"));
    }

    @Test
    @DisplayName("a month with no record yet gets one, basic from the staff profile")
    void createsRecord() {
        when(salaryRepository.findByEmployeeIdAndMonthAndYear(7L, 11, 2026)).thenReturn(Optional.empty());
        SalaryAdvanceRequestDTO r = req("500"); r.setMonth(11);
        SalaryDTO dto = salaryService.giveAdvance(r, "m");
        assertEquals(0, new BigDecimal("20000.00").compareTo(dto.getBasic()));
    }

    @Test
    @DisplayName("a paid month takes no advance and an advance cannot be removed from it")
    void paidMonthLocked() {
        salaryService.giveAdvance(req("2000"), "m");
        salary.setPaid(true);
        assertThrows(IllegalStateException.class, () -> salaryService.giveAdvance(req("100"), "m"));
        assertThrows(IllegalStateException.class, () -> salaryService.deleteAdvance(1L));
    }

    @Test
    @DisplayName("removing an advance gives the balance back")
    void removeAdvance() {
        salaryService.giveAdvance(req("2000"), "m");
        SalaryDTO dto = salaryService.deleteAdvance(1L);
        verify(advanceRepository).delete(any(SalaryAdvance.class));
        assertNotNull(dto);
    }

    @Test
    @DisplayName("editing the month cannot push net salary below the advance already given")
    void netCannotDropBelowAdvance() {
        salaryService.giveAdvance(req("5000"), "m");
        SalaryRequestDTO edit = new SalaryRequestDTO();
        edit.setEmployeeId(7L); edit.setMonth(10); edit.setYear(2026);
        edit.setBasic(new BigDecimal("4000")); edit.setAllowances(BigDecimal.ZERO); edit.setDeductions(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> salaryService.createOrUpdateSalary(edit));
    }
}
