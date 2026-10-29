package com.neram.pos.controller;

import com.neram.pos.dto.MoneyReportsDTO;
import com.neram.pos.service.BudgetService;
import com.neram.pos.service.DailyCloseService;
import com.neram.pos.service.StaffPerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Daily close report, budget against actual, staff performance and tips. Managers and admins only. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class MoneyReportsController {

    private final DailyCloseService dailyClose;
    private final BudgetService budget;
    private final StaffPerformanceService staff;

    private static String who(Authentication a) { return a != null ? a.getName() : null; }

    @GetMapping("/daily-close")
    public ResponseEntity<MoneyReportsDTO.DailyClose> dailyClose(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(dailyClose.build(date, LocalDate.now()));
    }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.TELEGRAM)
    @PostMapping("/daily-close/send")
    public ResponseEntity<MoneyReportsDTO.SendResult> sendDailyClose(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(dailyClose.send(date, LocalDate.now()));
    }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.TELEGRAM)
    @GetMapping("/daily-close/settings")
    public ResponseEntity<MoneyReportsDTO.DailyCloseSettings> dailyCloseSettings() { return ResponseEntity.ok(dailyClose.getSettings()); }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.TELEGRAM)
    @PutMapping("/daily-close/settings")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<MoneyReportsDTO.DailyCloseSettings> saveDailyCloseSettings(@RequestBody MoneyReportsDTO.DailyCloseSettings body, Authentication auth) {
        return ResponseEntity.ok(dailyClose.saveSettings(body, who(auth)));
    }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.SUPPLIERS_EXPENSES)
    @GetMapping("/budget")
    public ResponseEntity<MoneyReportsDTO.Budget> budget(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {
        LocalDate t = LocalDate.now();
        return ResponseEntity.ok(budget.report(year != null ? year : t.getYear(), month != null ? month : t.getMonthValue(), t));
    }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.SUPPLIERS_EXPENSES)
    @PutMapping("/budget")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<Void> setBudget(@RequestBody List<MoneyReportsDTO.BudgetUpdate> updates, Authentication auth) {
        if (updates == null || updates.isEmpty()) throw new IllegalArgumentException("Nothing to save.");
        for (MoneyReportsDTO.BudgetUpdate u : updates) budget.set(u.getCategory(), u.getMonthlyAmount(), who(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staff")
    public ResponseEntity<MoneyReportsDTO.StaffReport> staff(@RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(staff.report(period, startDate, endDate, LocalDate.now()));
    }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.STAFF_PAYROLL)
    @GetMapping("/tip-payouts")
    public ResponseEntity<List<MoneyReportsDTO.Payout>> payouts() { return ResponseEntity.ok(staff.recentPayouts()); }

    @com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.STAFF_PAYROLL)
    @PostMapping("/tip-payouts")
    public ResponseEntity<MoneyReportsDTO.Payout> pay(@RequestBody MoneyReportsDTO.PayoutRequest body, Authentication auth) {
        return ResponseEntity.ok(staff.pay(body.getStaffId(), body.getAmount(), body.getNote(), who(auth), LocalDate.now()));
    }
}
