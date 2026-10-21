package com.neram.pos.controller;

import com.neram.pos.dto.BillRefundDTO;
import com.neram.pos.service.BillRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BillRefundController {

    private final BillRefundService refundService;

    @PostMapping("/bills/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillRefundDTO.RefundResponse> processRefund(
            @PathVariable Long id,
            @RequestBody BillRefundDTO.RefundRequest req) {
        return ResponseEntity.ok(refundService.processRefund(id, req));
    }

    @PostMapping("/refunds")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillRefundDTO.RefundResponse> processRefundGeneric(
            @RequestBody BillRefundDTO.RefundRequest req) {
        return ResponseEntity.ok(refundService.processRefund(req.getBillId(), req));
    }

    @GetMapping("/bills/{id}/refunds")
    public ResponseEntity<List<BillRefundDTO.RefundResponse>> getRefundsForBill(@PathVariable Long id) {
        return ResponseEntity.ok(refundService.getRefundsForBill(id));
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<BillRefundDTO.RefundResponse>> getAllRefunds() {
        return ResponseEntity.ok(refundService.getAllRefunds());
    }
}
