package com.neram.pos.controller;

import com.neram.pos.dto.AccountingDTO.*;
import com.neram.pos.service.AccountingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
@com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.ACCOUNTING)
public class AccountingController {

    private final AccountingService accountingService;

    /** Managers see the profit and loss of their own branch (the branch filter limits the figures); owners and main managers see the whole cafe. */
    @GetMapping("/pl")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ProfitAndLossReport> getProfitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(accountingService.getProfitAndLoss(startDate, endDate));
    }

    /** Cash, stock, supplier payables and gift-card liability are cafe-level facts: owner and main manager only. */
    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<BalanceSheetReport> getBalanceSheet() {
        return ResponseEntity.ok(accountingService.getBalanceSheet());
    }

    @GetMapping("/gift-card-liability")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<GiftCardLiabilitySummary> getGiftCardLiability() {
        return ResponseEntity.ok(accountingService.getGiftCardLiability());
    }
}
