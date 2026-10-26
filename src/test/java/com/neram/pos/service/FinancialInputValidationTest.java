package com.neram.pos.service;

import com.neram.pos.dto.GstSettingDTO;
import com.neram.pos.dto.SalaryRequestDTO;
import com.neram.pos.dto.CashierShiftDTO;
import com.neram.pos.entity.CashierShift;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.GstSetting;
import com.neram.pos.entity.Salary;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialInputValidationTest {

    @Mock private GstSettingRepository gstSettingRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private GstSettingService gstSettingService;

    @Mock private SalaryRepository salaryRepository;
    @Mock private com.neram.pos.repository.SalaryAdvanceRepository salaryAdvanceRepository;
    @Mock private EmployeeRepository employeeRepository;
    @InjectMocks private SalaryService salaryService;

    @Mock private CashierShiftRepository shiftRepository;
    @Mock private CashMovementRepository movementRepository;
    @Mock private ShiftSettingRepository shiftSettingRepository;
    @Mock private BillRepository billRepository;
    @Mock private BillRefundRepository billRefundRepository;
    @Mock private CafeExpenseRepository cafeExpenseRepository;
    @InjectMocks private CashierShiftService shiftService;

    private GstSettingDTO gst(String gstin, Double cgst, Double sgst) {
        GstSettingDTO d = new GstSettingDTO();
        d.setGstin(gstin);
        d.setCgstPercent(cgst);
        d.setSgstPercent(sgst);
        return d;
    }

    private void stubGst() {
        when(gstSettingRepository.findById(1L)).thenReturn(Optional.of(GstSetting.builder()
                .id(1L).enabled(true).gstin("32CQRPS5214H3ZK").cgstPercent(2.5).sgstPercent(2.5).roundOffEnabled(false).build()));
        when(gstSettingRepository.save(any(GstSetting.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("GST settings reject a malformed GSTIN and out-of-range rates")
    void gstValidation() {
        stubGst();
        assertThrows(IllegalArgumentException.class, () -> gstSettingService.updateSettings(gst("BADGSTIN", 2.5, 2.5)));
        assertThrows(IllegalArgumentException.class, () -> gstSettingService.updateSettings(gst(null, 2.5, 200.0)));
        assertThrows(IllegalArgumentException.class, () -> gstSettingService.updateSettings(gst(null, -1.0, 2.5)));
        assertDoesNotThrow(() -> gstSettingService.updateSettings(gst("29abcde1234f1z5", 9.0, 9.0)));
    }

    private SalaryRequestDTO salary(int month, int year, String basic, String allowances, String deductions) {
        SalaryRequestDTO d = new SalaryRequestDTO();
        d.setEmployeeId(5L);
        d.setMonth(month);
        d.setYear(year);
        d.setBasic(new BigDecimal(basic));
        d.setAllowances(new BigDecimal(allowances));
        d.setDeductions(new BigDecimal(deductions));
        return d;
    }

    @Test
    @DisplayName("Salary rejects bad months, negative amounts, over-deduction and edits to a paid month")
    void salaryValidation() {
        Employee emp = new Employee();
        emp.setId(5L);
        emp.setName("QA");
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(emp));
        when(salaryRepository.findByEmployeeIdAndMonthAndYear(any(), any(), any())).thenReturn(Optional.empty());
        when(salaryRepository.save(any(Salary.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(IllegalArgumentException.class, () -> salaryService.createOrUpdateSalary(salary(13, 2026, "100", "0", "0")));
        assertThrows(IllegalArgumentException.class, () -> salaryService.createOrUpdateSalary(salary(0, 2026, "100", "0", "0")));
        assertThrows(IllegalArgumentException.class, () -> salaryService.createOrUpdateSalary(salary(5, 2026, "-1", "0", "0")));
        assertThrows(IllegalArgumentException.class, () -> salaryService.createOrUpdateSalary(salary(5, 2026, "1000", "0", "99999")));
        assertDoesNotThrow(() -> salaryService.createOrUpdateSalary(salary(5, 2026, "1000", "100", "50")));

        Salary paid = new Salary();
        paid.setId(9L);
        paid.setPaid(true);
        when(salaryRepository.findByEmployeeIdAndMonthAndYear(5L, 6, 2026)).thenReturn(Optional.of(paid));
        when(salaryRepository.findById(9L)).thenReturn(Optional.of(paid));
        assertThrows(IllegalStateException.class, () -> salaryService.createOrUpdateSalary(salary(6, 2026, "1000", "0", "0")));
        assertThrows(IllegalStateException.class, () -> salaryService.markAsPaid(9L, "CASH"));
    }

    @Test
    @DisplayName("Cash-out and drawer transfers cannot exceed the cash the drawer should hold")
    void drawerCannotGoNegative() {
        CashierShift shift = CashierShift.builder().id(3L).status(CashierShift.ShiftStatus.OPEN)
                .openingFloat(BigDecimal.valueOf(1000)).build();
        when(shiftRepository.findById(3L)).thenReturn(Optional.of(shift));
        when(shiftSettingRepository.findById(1L)).thenReturn(Optional.empty()); // module enabled by default

        CashierShiftDTO.CashMovementRequest out = new CashierShiftDTO.CashMovementRequest();
        out.setMovementType("CASH_OUT");
        out.setTransferType("PETTY_CASH");
        out.setAmount(BigDecimal.valueOf(1500));
        out.setReason("too much");
        assertThrows(IllegalArgumentException.class, () -> shiftService.recordCashMovement(3L, out));

        CashierShiftDTO.TransferRequest tr = new CashierShiftDTO.TransferRequest();
        tr.setAmount(BigDecimal.valueOf(1500));
        tr.setTransferType("DRAWER_TO_BANK");
        tr.setReason("deposit");
        assertThrows(IllegalArgumentException.class, () -> shiftService.recordTransfer(3L, tr));
        assertEquals(0, BigDecimal.ZERO.compareTo(shift.getCashOutTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(shift.getTransfersOutTotal()));
    }
}
