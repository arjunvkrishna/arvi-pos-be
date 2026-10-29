package com.neram.pos.controller;

import com.neram.pos.dto.BillLogSummaryDTO;
import com.neram.pos.dto.BillRequestDTO;
import com.neram.pos.dto.BillResponseDTO;
import com.neram.pos.service.BillService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;
    private final com.neram.pos.service.IdempotentOrders idempotentOrders;
    private final com.neram.pos.service.NotificationStreamService notificationStreamService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillResponseDTO> createBill(@RequestBody BillRequestDTO request) {
        return ResponseEntity.ok(billService.createBill(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<BillResponseDTO>> getAllBills() {
        return ResponseEntity.ok(billService.getAllBills());
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<BillResponseDTO>> searchBills(@RequestParam("q") String q) {
        return ResponseEntity.ok(billService.searchBills(q));
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<com.neram.pos.dto.BillLogRowDTO>> getDailyBills(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String staff,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return PagedResponse.of(billService.getBillLog(false, date, null, null, orderType, staff, q, pageable));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<com.neram.pos.dto.BillLogRowDTO>> getMonthlyBills(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String staff,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return PagedResponse.of(billService.getBillLog(true, null, year, month, orderType, staff, q, pageable));
    }

    /** Totals above the Bill Log for the same filters as /daily (mode=daily) or /monthly (mode=monthly). */
    @GetMapping("/log/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillLogSummaryDTO> getBillLogSummary(
            @RequestParam(defaultValue = "daily") String mode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String staff,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(billService.getBillLogSummary("monthly".equalsIgnoreCase(mode), date, year, month, orderType, staff, q));
    }

    /** Usernames for the Bill Log's staff filter in the chosen day or month. */
    @GetMapping("/log/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<String>> getBillLogStaff(
            @RequestParam(defaultValue = "daily") String mode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(billService.getBillLogStaff("monthly".equalsIgnoreCase(mode), date, year, month));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillResponseDTO> getBillById(@PathVariable Long id) {
        return ResponseEntity.ok(billService.getBillById(id));
    }

    @GetMapping("/number/{billNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillResponseDTO> getBillByNumber(@PathVariable String billNumber) {
        BillResponseDTO dto = billService.getBillByNumber(billNumber);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/print")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_PRINT_BILL')")
    public ResponseEntity<BillResponseDTO> markAsPrinted(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markAsPrinted(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_CANCEL_ORDER')")
    public ResponseEntity<BillResponseDTO> cancelBill(@PathVariable Long id) {
        return ResponseEntity.ok(billService.cancelBill(id));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_CANCEL_ORDER')")
    public ResponseEntity<BillResponseDTO> voidBill(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Boolean foodWasPrepared,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String effectiveReason = reason;
        Boolean effectiveFoodPrepared = foodWasPrepared;
        if (body != null) {
            if (effectiveReason == null && body.containsKey("reason")) {
                effectiveReason = String.valueOf(body.get("reason"));
            }
            if (effectiveFoodPrepared == null && body.containsKey("foodWasPrepared")) {
                effectiveFoodPrepared = Boolean.parseBoolean(String.valueOf(body.get("foodWasPrepared")));
            }
        }
        return ResponseEntity.ok(billService.voidBill(id, effectiveReason, effectiveFoodPrepared != null ? effectiveFoodPrepared : false));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillResponseDTO> updateBill(@PathVariable Long id, @RequestBody BillRequestDTO request) {
        return ResponseEntity.ok(billService.updateBill(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBill(@PathVariable Long id) {
        billService.deleteBill(id);
        return ResponseEntity.noContent().build();
    }

    /** Ids of the bills the Bill Log may offer a Delete button for (the 5 newest closed bills). */
    @GetMapping("/deletable-ids")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Long>> getDeletableBillIds() {
        return ResponseEntity.ok(billService.getDeletableBillIds());
    }

    /** Soft delete from the Bill Log: only the 5 newest closed bills, reason mandatory, recorded in the audit log. */
    @PostMapping("/{id}/soft-delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> softDeleteBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, Object> body) {
        Object reason = body != null ? body.get("reason") : null;
        billService.softDeleteBill(id, reason != null ? String.valueOf(reason) : null);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // TABLE ORDERS & KITCHEN ORDER TICKETS (KOT)
    // Accessible by STAFF (EMPLOYEE), MANAGER, and ADMIN
    // ============================================================

    @PostMapping("/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<BillResponseDTO> saveOrUpdateTableOrder(@RequestBody com.neram.pos.dto.TableOrderRequestDTO request,
                                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(idempotentOrders.save(idempotencyKey, request));
    }

    /**
     * SSE signal ("orders-changed") replacing the 3s polling of /tables and /bills/active. Same access rule as /active, plus the
     * kitchen role: the signal carries no data, the kitchen display then reads /api/kitchen/tickets.
     */
    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'KITCHEN') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> streamOrderChanges(org.springframework.security.core.Authentication auth) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                notificationStreamService.subscribe(auth.getName(), com.neram.pos.service.NotificationStreamService.ORDERS_EVENT);
        if (emitter == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok().header("Cache-Control", "no-cache").header("X-Accel-Buffering", "no").body(emitter);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<List<BillResponseDTO>> getActiveOrders() {
        return ResponseEntity.ok(billService.getActiveOrders());
    }

    @GetMapping("/table/{tableNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<BillResponseDTO> getActiveOrderByTable(@PathVariable String tableNumber) {
        BillResponseDTO order = billService.getActiveOrderByTable(tableNumber);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/next-outdoor-token")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<java.util.Map<String, String>> getNextOutdoorToken() {
        return ResponseEntity.ok(java.util.Map.of("outdoorToken", billService.getNextOutdoorToken()));
    }

    @GetMapping("/outdoor/{outdoorToken}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<BillResponseDTO> getActiveOrderByOutdoorToken(@PathVariable String outdoorToken) {
        BillResponseDTO order = billService.getActiveOrderByOutdoorToken(outdoorToken);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/next-takeaway-token")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<java.util.Map<String, String>> getNextTakeawayToken() {
        return ResponseEntity.ok(java.util.Map.of("takeawayToken", billService.getNextTakeawayToken()));
    }

    @GetMapping("/takeaway/{takeawayToken}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<BillResponseDTO> getActiveOrderByTakeawayToken(@PathVariable String takeawayToken) {
        BillResponseDTO order = billService.getActiveOrderByTakeawayToken(takeawayToken);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/change-location")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<BillResponseDTO> changeOrderLocation(
            @PathVariable Long id,
            @RequestBody com.neram.pos.dto.ChangeLocationRequestDTO request) {
        return ResponseEntity.ok(billService.changeOrderLocation(id, request));
    }

    @PostMapping("/{id}/request-kot")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<BillResponseDTO> requestKitchenOrder(@PathVariable Long id) {
        return ResponseEntity.ok(billService.requestKitchenOrder(id));
    }

    @PostMapping("/{id}/print-kot")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<BillResponseDTO> printKitchenOrder(@PathVariable Long id) {
        return ResponseEntity.ok(billService.printKitchenOrder(id));
    }

    @PostMapping("/{id}/request-bill")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<BillResponseDTO> requestBill(@PathVariable Long id) {
        return ResponseEntity.ok(billService.requestBill(id));
    }

    @PostMapping("/{id}/approve-bill")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillResponseDTO> approveAndPayBill(@PathVariable Long id, @RequestBody(required = false) BillRequestDTO request) {
        return ResponseEntity.ok(billService.approveAndPayBill(id, request));
    }

    @PostMapping("/{id}/print-bill")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_PRINT_BILL')")
    public ResponseEntity<BillResponseDTO> printBill(@PathVariable Long id, @RequestBody(required = false) BillRequestDTO request) {
        return ResponseEntity.ok(billService.printBill(id, request));
    }

    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BillResponseDTO> confirmPayment(@PathVariable Long id, @RequestBody(required = false) BillRequestDTO request) {
        return ResponseEntity.ok(billService.confirmPayment(id, request));
    }
}
