package com.neram.pos.service;

import com.neram.pos.dto.CafeExpenseDTO;
import com.neram.pos.dto.CafeExpenseRequestDTO;
import com.neram.pos.dto.ExpenseTemplateDTO;
import com.neram.pos.dto.ExpenseTemplateRequestDTO;
import com.neram.pos.entity.CafeExpense;
import com.neram.pos.entity.ExpenseTemplate;
import com.neram.pos.repository.CafeExpenseRepository;
import com.neram.pos.repository.ExpenseTemplateRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExpenseSingleSubmitTest {

    @Mock
    private CafeExpenseRepository expenseRepository;

    @Mock
    private ExpenseTemplateRepository templateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        when(expenseRepository.save(any(CafeExpense.class))).thenAnswer(invocation -> {
            CafeExpense entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(101L);
            }
            return entity;
        });

        when(templateRepository.save(any(ExpenseTemplate.class))).thenAnswer(invocation -> {
            ExpenseTemplate tpl = invocation.getArgument(0);
            if (tpl.getId() == null) {
                tpl.setId(201L);
            }
            return tpl;
        });
    }

    @Test
    @DisplayName("Single submit of createExpense creates exactly one expense record")
    void testCreateExpense_SingleSubmit() {
        CafeExpenseRequestDTO request = new CafeExpenseRequestDTO();
        request.setType("RENT");
        request.setDescription("Shop Monthly Rent");
        request.setAmount(new BigDecimal("35000.00"));
        request.setExpenseDate(LocalDate.of(2026, 10, 5));
        request.setVendorOrPayee("Landlord");
        request.setPaymentMethod("Bank Transfer");
        request.setStatus("PAID");

        CafeExpenseDTO response = expenseService.createExpense(request);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("Shop Monthly Rent", response.getDescription());
        assertEquals(new BigDecimal("35000.00"), response.getAmount());

        // Verify save was called exactly once on repository
        verify(expenseRepository, times(1)).save(any(CafeExpense.class));
        verify(auditLogService, times(1)).log(eq("CREATE_EXPENSE"), eq("EXPENSE"), eq(101L), any(), anyString());
    }

    @Test
    @DisplayName("Editing an existing expense updates the record without creating a duplicate")
    void testUpdateExpense_ModifiesExisting() {
        CafeExpense existing = new CafeExpense();
        existing.setId(101L);
        existing.setType(CafeExpense.ExpenseType.RENT);
        existing.setDescription("Shop Monthly Rent");
        existing.setAmount(new BigDecimal("35000.00"));
        existing.setExpenseDate(LocalDate.of(2026, 10, 5));
        existing.setStatus("PENDING");

        when(expenseRepository.findById(101L)).thenReturn(Optional.of(existing));

        CafeExpenseRequestDTO updateReq = new CafeExpenseRequestDTO();
        updateReq.setType("RENT");
        updateReq.setDescription("Shop Monthly Rent (Updated via NEFT)");
        updateReq.setAmount(new BigDecimal("35500.00"));
        updateReq.setExpenseDate(LocalDate.of(2026, 10, 5));
        updateReq.setStatus("PAID");
        updateReq.setPaymentMethod("Bank Transfer");

        CafeExpenseDTO updated = expenseService.updateExpense(101L, updateReq);

        assertNotNull(updated);
        assertEquals(101L, updated.getId());
        assertEquals("Shop Monthly Rent (Updated via NEFT)", updated.getDescription());
        assertEquals(new BigDecimal("35500.00"), updated.getAmount());
        assertEquals("PAID", updated.getStatus());

        // Exactly one save to update existing entity
        verify(expenseRepository, times(1)).save(existing);
        verify(auditLogService, times(1)).log(eq("UPDATE_EXPENSE"), eq("EXPENSE"), eq(101L), anyString(), anyString());
    }

    @Test
    @DisplayName("Creating recurring template creates exactly one template record")
    void testCreateRecurringTemplate_SingleSubmit() {
        ExpenseTemplateRequestDTO tplReq = new ExpenseTemplateRequestDTO();
        tplReq.setTitle("Espresso Machine EMI");
        tplReq.setType("EMI");
        tplReq.setAmount(new BigDecimal("15000.00"));
        tplReq.setFrequency("MONTHLY");
        tplReq.setDueDay(10);
        tplReq.setVendorOrPayee("HDFC Bank");
        tplReq.setPaymentMethod("Auto-Debit");
        tplReq.setIsActive(true);

        ExpenseTemplateDTO createdTpl = expenseService.createTemplate(tplReq);

        assertNotNull(createdTpl);
        assertEquals(201L, createdTpl.getId());
        assertEquals("Espresso Machine EMI", createdTpl.getTitle());
        assertEquals(new BigDecimal("15000.00"), createdTpl.getAmount());

        verify(templateRepository, times(1)).save(any(ExpenseTemplate.class));
        verify(auditLogService, times(1)).log(eq("CREATE_EXPENSE_TEMPLATE"), eq("EXPENSE_TEMPLATE"), eq(201L), any(), anyString());
    }
}
