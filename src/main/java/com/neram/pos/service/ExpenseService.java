package com.neram.pos.service;

import com.neram.pos.dto.CafeExpenseDTO;
import com.neram.pos.dto.CafeExpenseRequestDTO;
import com.neram.pos.dto.ExpenseTemplateDTO;
import com.neram.pos.dto.ExpenseTemplateRequestDTO;
import com.neram.pos.dto.RecurringExpenseStatusDTO;
import com.neram.pos.entity.CafeExpense;
import com.neram.pos.entity.ExpenseTemplate;
import com.neram.pos.entity.User;
import com.neram.pos.repository.CafeExpenseRepository;
import com.neram.pos.repository.ExpenseTemplateRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final CafeExpenseRepository expenseRepository;
    private final ExpenseTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // ============================================================
    // 1. STANDARD EXPENSES CRUD & RETRIEVAL
    // ============================================================

    public List<CafeExpenseDTO> getAllExpenses() {
        return expenseRepository.findAllByOrderByExpenseDateDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<CafeExpenseDTO> getExpensesByType(String type) {
        CafeExpense.ExpenseType expenseType = CafeExpense.ExpenseType.valueOf(type.toUpperCase());
        return expenseRepository.findByType(expenseType).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<CafeExpenseDTO> getExpensesByMonth(int month, int year) {
        return expenseRepository.findByMonthAndYearOrderByExpenseDateDesc(month, year).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CafeExpenseDTO createExpense(CafeExpenseRequestDTO dto) {
        CafeExpense exp = new CafeExpense();
        exp.setType(CafeExpense.ExpenseType.valueOf(dto.getType().toUpperCase()));
        exp.setDescription(dto.getDescription());
        exp.setAmount(dto.getAmount());
        LocalDate date = dto.getExpenseDate() != null ? dto.getExpenseDate() : LocalDate.now();
        exp.setExpenseDate(date);
        exp.setMonth(date.getMonthValue());
        exp.setYear(date.getYear());
        exp.setPaidBy(dto.getPaidBy());
        exp.setVendorOrPayee(dto.getVendorOrPayee());
        exp.setPaymentMethod(dto.getPaymentMethod() != null && !dto.getPaymentMethod().isBlank() 
                ? dto.getPaymentMethod() : dto.getPaidBy());
        exp.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank() 
                ? dto.getStatus().toUpperCase() : "PAID");
        exp.setDueDate(dto.getDueDate());
        exp.setTemplateId(dto.getTemplateId());
        exp.setIsRecurring(Boolean.TRUE.equals(dto.getIsRecurring()));
        exp.setReceiptRef(dto.getReceiptRef());
        exp.setNotes(dto.getNotes());
        exp.setCreatedAt(LocalDateTime.now());

        assignCurrentUser(exp);

        CafeExpense saved = expenseRepository.save(exp);
        auditLogService.log("CREATE_EXPENSE", "EXPENSE", saved.getId(), null, saved.getType() + " - ₹" + saved.getAmount());
        return toDTO(saved);
    }

    @Transactional
    public CafeExpenseDTO updateExpense(Long id, CafeExpenseRequestDTO dto) {
        CafeExpense exp = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));

        String oldVal = exp.getType() + " - ₹" + exp.getAmount();
        if (dto.getType() != null) exp.setType(CafeExpense.ExpenseType.valueOf(dto.getType().toUpperCase()));
        exp.setDescription(dto.getDescription());
        exp.setAmount(dto.getAmount());
        if (dto.getExpenseDate() != null) {
            exp.setExpenseDate(dto.getExpenseDate());
            exp.setMonth(dto.getExpenseDate().getMonthValue());
            exp.setYear(dto.getExpenseDate().getYear());
        }
        exp.setPaidBy(dto.getPaidBy());
        exp.setVendorOrPayee(dto.getVendorOrPayee());
        if (dto.getPaymentMethod() != null) exp.setPaymentMethod(dto.getPaymentMethod());
        if (dto.getStatus() != null) exp.setStatus(dto.getStatus().toUpperCase());
        if (dto.getDueDate() != null) exp.setDueDate(dto.getDueDate());
        if (dto.getTemplateId() != null) exp.setTemplateId(dto.getTemplateId());
        if (dto.getIsRecurring() != null) exp.setIsRecurring(dto.getIsRecurring());
        exp.setReceiptRef(dto.getReceiptRef());
        exp.setNotes(dto.getNotes());

        CafeExpense updated = expenseRepository.save(exp);
        auditLogService.log("UPDATE_EXPENSE", "EXPENSE", updated.getId(), oldVal, updated.getType() + " - ₹" + updated.getAmount());
        return toDTO(updated);
    }

    @Transactional
    public CafeExpenseDTO updateExpenseStatus(Long id, String status, String paymentMethod) {
        CafeExpense exp = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        String oldStatus = exp.getStatus();
        exp.setStatus(status.toUpperCase());
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            exp.setPaymentMethod(paymentMethod);
            exp.setPaidBy(paymentMethod);
        }
        if ("PAID".equalsIgnoreCase(status) && exp.getExpenseDate() == null) {
            exp.setExpenseDate(LocalDate.now());
            exp.setMonth(LocalDate.now().getMonthValue());
            exp.setYear(LocalDate.now().getYear());
        }
        CafeExpense updated = expenseRepository.save(exp);
        auditLogService.log("UPDATE_EXPENSE_STATUS", "EXPENSE", id, oldStatus, updated.getStatus());
        return toDTO(updated);
    }

    @Transactional
    public void deleteExpense(Long id) {
        CafeExpense exp = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        expenseRepository.delete(exp);
        auditLogService.log("DELETE_EXPENSE", "EXPENSE", id, exp.getDescription(), "Deleted ₹" + exp.getAmount());
    }

    // ============================================================
    // 2. EXPENSE TEMPLATES (RECURRING / EMI COMMITTED EXPENSES)
    // ============================================================

    public List<ExpenseTemplateDTO> getAllTemplates(Boolean onlyActive) {
        List<ExpenseTemplate> list = Boolean.TRUE.equals(onlyActive)
                ? templateRepository.findByIsActiveTrueOrderByDueDayAsc()
                : templateRepository.findAllByOrderByDueDayAsc();
        return list.stream().map(this::toTemplateDTO).collect(Collectors.toList());
    }

    /**
     * One page of expenses, newest first, narrowed by whichever filters are given (all optional): period, type, status and a
     * text search over description, payee, receipt reference and notes.
     */
    public org.springframework.data.domain.Page<CafeExpenseDTO> searchExpenses(Integer month, Integer year, String type, String status,
                                                                               String q, org.springframework.data.domain.Pageable pageable) {
        CafeExpense.ExpenseType expenseType = (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type))
                ? CafeExpense.ExpenseType.valueOf(type.trim().toUpperCase()) : null;
        String statusFilter = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) ? status.trim() : null;
        String pattern = com.neram.pos.util.LikeSearch.pattern(q);

        org.springframework.data.jpa.domain.Specification<CafeExpense> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            if (month != null) p.add(cb.equal(root.get("month"), month));
            if (year != null) p.add(cb.equal(root.get("year"), year));
            if (expenseType != null) p.add(cb.equal(root.get("type"), expenseType));
            if (statusFilter != null) p.add(cb.equal(root.get("status"), statusFilter));
            if (pattern != null) {
                p.add(cb.or(
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("description"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("vendorOrPayee"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("receiptRef"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("notes"), pattern)));
            }
            return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        org.springframework.data.domain.Sort newestFirst = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("expenseDate"), org.springframework.data.domain.Sort.Order.desc("id"));
        return expenseRepository.findAll(spec, com.neram.pos.util.PagedResponse.withSort(pageable, newestFirst)).map(this::toDTO);
    }

    /** One page of recurring templates, in due-day order. */
    public org.springframework.data.domain.Page<ExpenseTemplateDTO> getAllTemplates(Boolean onlyActive, org.springframework.data.domain.Pageable pageable) {
        return (Boolean.TRUE.equals(onlyActive)
                ? templateRepository.findByIsActiveTrueOrderByDueDayAscIdAsc(pageable)
                : templateRepository.findAllByOrderByDueDayAscIdAsc(pageable)).map(this::toTemplateDTO);
    }

    public ExpenseTemplateDTO getTemplateById(Long id) {
        return templateRepository.findById(id)
                .map(this::toTemplateDTO)
                .orElseThrow(() -> new RuntimeException("Expense template not found with id: " + id));
    }

    @Transactional
    public ExpenseTemplateDTO createTemplate(ExpenseTemplateRequestDTO dto) {
        ExpenseTemplate template = new ExpenseTemplate();
        template.setTitle(dto.getTitle());
        template.setType(CafeExpense.ExpenseType.valueOf(dto.getType().toUpperCase()));
        template.setAmount(dto.getAmount());
        if (dto.getFrequency() != null && !dto.getFrequency().isBlank()) {
            template.setFrequency(ExpenseTemplate.Frequency.valueOf(dto.getFrequency().toUpperCase()));
        } else {
            template.setFrequency(ExpenseTemplate.Frequency.MONTHLY);
        }
        template.setDueDay(dto.getDueDay() != null ? Math.min(31, Math.max(1, dto.getDueDay())) : 1);
        template.setVendorOrPayee(dto.getVendorOrPayee());
        template.setPaymentMethod(dto.getPaymentMethod());
        template.setNotes(dto.getNotes());
        template.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());

        assignCurrentUserToTemplate(template);

        ExpenseTemplate saved = templateRepository.save(template);
        auditLogService.log("CREATE_EXPENSE_TEMPLATE", "EXPENSE_TEMPLATE", saved.getId(), null, saved.getTitle() + " - ₹" + saved.getAmount());
        return toTemplateDTO(saved);
    }

    @Transactional
    public ExpenseTemplateDTO updateTemplate(Long id, ExpenseTemplateRequestDTO dto) {
        ExpenseTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense template not found with id: " + id));

        String oldVal = t.getTitle() + " - ₹" + t.getAmount();
        t.setTitle(dto.getTitle());
        if (dto.getType() != null) t.setType(CafeExpense.ExpenseType.valueOf(dto.getType().toUpperCase()));
        t.setAmount(dto.getAmount());
        if (dto.getFrequency() != null && !dto.getFrequency().isBlank()) {
            t.setFrequency(ExpenseTemplate.Frequency.valueOf(dto.getFrequency().toUpperCase()));
        }
        if (dto.getDueDay() != null) {
            t.setDueDay(Math.min(31, Math.max(1, dto.getDueDay())));
        }
        t.setVendorOrPayee(dto.getVendorOrPayee());
        t.setPaymentMethod(dto.getPaymentMethod());
        t.setNotes(dto.getNotes());
        if (dto.getIsActive() != null) t.setIsActive(dto.getIsActive());
        t.setUpdatedAt(LocalDateTime.now());

        ExpenseTemplate updated = templateRepository.save(t);
        auditLogService.log("UPDATE_EXPENSE_TEMPLATE", "EXPENSE_TEMPLATE", updated.getId(), oldVal, updated.getTitle() + " - ₹" + updated.getAmount());
        return toTemplateDTO(updated);
    }

    @Transactional
    public ExpenseTemplateDTO toggleTemplateActive(Long id) {
        ExpenseTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense template not found with id: " + id));
        t.setIsActive(!Boolean.TRUE.equals(t.getIsActive()));
        t.setUpdatedAt(LocalDateTime.now());
        ExpenseTemplate updated = templateRepository.save(t);
        auditLogService.log("TOGGLE_EXPENSE_TEMPLATE", "EXPENSE_TEMPLATE", id, null, "Active: " + updated.getIsActive());
        return toTemplateDTO(updated);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        ExpenseTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense template not found with id: " + id));
        templateRepository.delete(t);
        auditLogService.log("DELETE_EXPENSE_TEMPLATE", "EXPENSE_TEMPLATE", id, t.getTitle(), "Deleted template");
    }

    // ============================================================
    // 3. RECURRING LOGGING & GENERATION ENGINE
    // ============================================================

    @Transactional
    public CafeExpenseDTO generateExpenseFromTemplate(Long templateId, LocalDate expenseDate, String statusOverride) {
        ExpenseTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Expense template not found with id: " + templateId));

        LocalDate date = expenseDate != null ? expenseDate : LocalDate.now();
        int month = date.getMonthValue();
        int year = date.getYear();

        // Check if an expense for this template already exists in this month/year
        List<CafeExpense> existing = expenseRepository.findByTemplateIdAndMonthAndYear(templateId, month, year);
        if (!existing.isEmpty()) {
            log.info("Template ID {} already has {} expense(s) logged for {}/{}", templateId, existing.size(), month, year);
            // Return existing expense if already recorded
            return toDTO(existing.get(0));
        }

        // Calculate due date in this month
        int safeDay = Math.min(template.getDueDay() != null ? template.getDueDay() : 1, date.lengthOfMonth());
        LocalDate dueDate = LocalDate.of(year, month, safeDay);

        CafeExpense exp = new CafeExpense();
        exp.setType(template.getType());
        exp.setDescription(template.getTitle());
        exp.setAmount(template.getAmount());
        exp.setExpenseDate(date);
        exp.setMonth(month);
        exp.setYear(year);
        exp.setDueDate(dueDate);
        exp.setVendorOrPayee(template.getVendorOrPayee());
        exp.setPaymentMethod(template.getPaymentMethod());
        exp.setPaidBy(template.getPaymentMethod());
        exp.setStatus(statusOverride != null && !statusOverride.isBlank() ? statusOverride.toUpperCase() : "PAID");
        exp.setTemplateId(template.getId());
        exp.setIsRecurring(true);
        exp.setNotes(template.getNotes());
        exp.setCreatedAt(LocalDateTime.now());

        assignCurrentUser(exp);

        CafeExpense saved = expenseRepository.save(exp);
        template.setLastGeneratedDate(date);
        template.setUpdatedAt(LocalDateTime.now());
        templateRepository.save(template);

        auditLogService.log("GENERATE_RECURRING_EXPENSE", "EXPENSE", saved.getId(), null, 
                "From template '" + template.getTitle() + "' - ₹" + saved.getAmount());
        return toDTO(saved);
    }

    @Transactional
    public List<CafeExpenseDTO> generateAllDueRecurring(Integer month, Integer year, LocalDate expenseDate) {
        LocalDate now = LocalDate.now();
        int targetMonth = month != null ? month : now.getMonthValue();
        int targetYear = year != null ? year : now.getYear();
        LocalDate postDate = expenseDate != null ? expenseDate : LocalDate.of(targetYear, targetMonth, Math.min(now.getDayOfMonth(), LocalDate.of(targetYear, targetMonth, 1).lengthOfMonth()));

        List<ExpenseTemplate> activeTemplates = templateRepository.findByIsActiveTrueOrderByDueDayAsc();
        List<CafeExpenseDTO> generated = new ArrayList<>();

        for (ExpenseTemplate t : activeTemplates) {
            List<CafeExpense> existing = expenseRepository.findByTemplateIdAndMonthAndYear(t.getId(), targetMonth, targetYear);
            if (existing.isEmpty()) {
                CafeExpenseDTO newExp = generateExpenseFromTemplate(t.getId(), postDate, "PAID");
                generated.add(newExp);
            }
        }
        return generated;
    }

    public List<RecurringExpenseStatusDTO> getRecurringStatusForMonth(Integer month, Integer year) {
        LocalDate now = LocalDate.now();
        int targetMonth = month != null ? month : now.getMonthValue();
        int targetYear = year != null ? year : now.getYear();

        List<ExpenseTemplate> templates = templateRepository.findAllByOrderByDueDayAsc();
        List<RecurringExpenseStatusDTO> statusList = new ArrayList<>();

        for (ExpenseTemplate t : templates) {
            List<CafeExpense> existing = expenseRepository.findByTemplateIdAndMonthAndYear(t.getId(), targetMonth, targetYear);
            boolean isRecorded = !existing.isEmpty();
            CafeExpense recordedExp = isRecorded ? existing.get(0) : null;

            String status = "UNRECORDED";
            if (isRecorded) {
                status = recordedExp.getStatus() != null ? recordedExp.getStatus() : "PAID";
            } else {
                // If past due day in current/past month, mark OVERDUE
                int dueDay = t.getDueDay() != null ? t.getDueDay() : 1;
                LocalDate targetDue = LocalDate.of(targetYear, targetMonth, Math.min(dueDay, LocalDate.of(targetYear, targetMonth, 1).lengthOfMonth()));
                if (now.isAfter(targetDue)) {
                    status = "OVERDUE";
                } else {
                    status = "DUE_SOON";
                }
            }

            statusList.add(RecurringExpenseStatusDTO.builder()
                    .templateId(t.getId())
                    .title(t.getTitle())
                    .type(t.getType().name())
                    .expectedAmount(t.getAmount())
                    .dueDay(t.getDueDay())
                    .vendorOrPayee(t.getVendorOrPayee())
                    .paymentMethod(t.getPaymentMethod())
                    .status(status)
                    .isRecorded(isRecorded)
                    .expenseId(recordedExp != null ? recordedExp.getId() : null)
                    .expenseDate(recordedExp != null ? recordedExp.getExpenseDate() : null)
                    .recordedAmount(recordedExp != null ? recordedExp.getAmount() : null)
                    .build());
        }
        return statusList;
    }

    // ============================================================
    // 4. MAPPING HELPERS
    // ============================================================

    public CafeExpenseDTO toDTO(CafeExpense e) {
        String createdByName = "Staff";
        if (e.getCreatedBy() != null) {
            createdByName = userRepository.findById(e.getCreatedBy()).map(User::getUsername).orElse("Staff");
        }
        return CafeExpenseDTO.builder()
                .id(e.getId())
                .type(e.getType().name())
                .description(e.getDescription())
                .amount(e.getAmount())
                .expenseDate(e.getExpenseDate())
                .month(e.getMonth())
                .year(e.getYear())
                .paidBy(e.getPaidBy())
                .vendorOrPayee(e.getVendorOrPayee())
                .paymentMethod(e.getPaymentMethod())
                .status(e.getStatus() != null ? e.getStatus() : "PAID")
                .dueDate(e.getDueDate())
                .templateId(e.getTemplateId())
                .isRecurring(Boolean.TRUE.equals(e.getIsRecurring()))
                .receiptRef(e.getReceiptRef())
                .notes(e.getNotes())
                .createdBy(e.getCreatedBy())
                .createdByName(createdByName)
                .createdAt(e.getCreatedAt())
                .build();
    }

    public ExpenseTemplateDTO toTemplateDTO(ExpenseTemplate t) {
        String createdByName = "Staff";
        if (t.getCreatedBy() != null) {
            createdByName = userRepository.findById(t.getCreatedBy()).map(User::getUsername).orElse("Staff");
        }
        return ExpenseTemplateDTO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .type(t.getType().name())
                .amount(t.getAmount())
                .frequency(t.getFrequency().name())
                .dueDay(t.getDueDay())
                .vendorOrPayee(t.getVendorOrPayee())
                .paymentMethod(t.getPaymentMethod())
                .notes(t.getNotes())
                .isActive(Boolean.TRUE.equals(t.getIsActive()))
                .lastGeneratedDate(t.getLastGeneratedDate())
                .createdBy(t.getCreatedBy())
                .createdByName(createdByName)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private void assignCurrentUser(CafeExpense exp) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> exp.setCreatedBy(u.getId()));
            }
        } catch (Exception ignored) {}
    }

    private void assignCurrentUserToTemplate(ExpenseTemplate template) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> template.setCreatedBy(u.getId()));
            }
        } catch (Exception ignored) {}
    }
}
