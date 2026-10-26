package com.neram.pos.controller;

import com.neram.pos.dto.*;
import com.neram.pos.repository.UserRepository;
import com.neram.pos.service.SupplierService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final UserRepository userRepository;

    // ─── Supplier Master ─────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SupplierDTO>> getAllSuppliers(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String scope,
            Pageable pageable) {
        // Paged / screen-filtered request (status, text, scope): the Suppliers screens
        if (pageable.isPaged() || status != null || text != null || scope != null) {
            String effectiveStatus = status != null ? status : (activeOnly ? "ACTIVE" : "ALL");
            return PagedResponse.of(supplierService.getSuppliers(effectiveStatus, category, text, phone, "overview".equalsIgnoreCase(scope), pageable));
        }
        if ((query != null && !query.trim().isEmpty()) || (phone != null && !phone.trim().isEmpty()) || (category != null && !category.trim().isEmpty())) {
            return ResponseEntity.ok(supplierService.searchSuppliers(query, phone, category, activeOnly));
        }
        return ResponseEntity.ok(supplierService.getAllSuppliers(activeOnly));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SupplierDTO>> searchSuppliers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(supplierService.searchSuppliers(query, phone, category, activeOnly));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierDTO> getSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplier(id));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierDetailsDTO> getSupplierDetails(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierDetails(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierDTO> createSupplier(
            @RequestBody SupplierRequestDTO req, Authentication auth) {
        return ResponseEntity.ok(supplierService.createSupplier(req, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierDTO> updateSupplier(
            @PathVariable Long id, @RequestBody SupplierRequestDTO req, Authentication auth) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, req, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, String>> deleteSupplier(
            @PathVariable Long id, Authentication auth) {
        supplierService.deleteSupplier(id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Supplier deactivated successfully"));
    }

    // ─── Invoices ────────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SupplierInvoiceDTO>> getAllInvoices(
            @RequestParam(required = false) String status, Pageable pageable) {
        return PagedResponse.of(supplierService.getInvoices(null, status, pageable));
    }

    @GetMapping("/invoices/outstanding")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SupplierInvoiceDTO>> getOutstandingInvoices() {
        return ResponseEntity.ok(supplierService.getOutstandingInvoices());
    }

    @GetMapping("/{supplierId}/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SupplierInvoiceDTO>> getInvoicesBySupplier(
            @PathVariable Long supplierId, @RequestParam(required = false) String status, Pageable pageable) {
        return PagedResponse.of(supplierService.getInvoices(supplierId, status, pageable));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierInvoiceDTO> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getInvoice(id));
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierInvoiceDTO> createInvoice(
            @RequestBody SupplierInvoiceRequestDTO req, Authentication auth) {
        Long userId = getUserId(auth);
        return ResponseEntity.ok(supplierService.createInvoice(req, userId, auth.getName()));
    }

    @PutMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierInvoiceDTO> updateInvoice(
            @PathVariable Long id, @RequestBody SupplierInvoiceRequestDTO req, Authentication auth) {
        return ResponseEntity.ok(supplierService.updateInvoice(id, req, auth.getName()));
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, String>> deleteInvoice(
            @PathVariable Long id, Authentication auth) {
        supplierService.deleteInvoice(id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Invoice deleted successfully"));
    }

    // ─── Payments ────────────────────────────────────────────────────────────────

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SupplierPaymentDTO>> getAllPayments(Pageable pageable) {
        return PagedResponse.of(supplierService.getPayments(null, pageable));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SupplierPaymentDTO>> getPaymentsByInvoice(
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(supplierService.getPaymentsByInvoice(invoiceId));
    }

    @GetMapping("/{supplierId}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SupplierPaymentDTO>> getPaymentsBySupplier(
            @PathVariable Long supplierId, Pageable pageable) {
        return PagedResponse.of(supplierService.getPayments(supplierId, pageable));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierPaymentDTO> addPayment(
            @RequestBody SupplierPaymentRequestDTO req, Authentication auth) {
        Long userId = getUserId(auth);
        return ResponseEntity.ok(supplierService.addPayment(req, userId, auth.getName()));
    }

    @DeleteMapping("/payments/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deletePayment(
            @PathVariable Long paymentId, Authentication auth) {
        supplierService.deletePayment(paymentId, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Payment reversed successfully"));
    }

    // ─── Analytics ───────────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SupplierAnalyticsDTO> getAnalytics() {
        return ResponseEntity.ok(supplierService.getAnalytics());
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────

    private Long getUserId(Authentication auth) {
        if (auth == null) return null;
        return userRepository.findByUsername(auth.getName())
                .map(u -> u.getId()).orElse(null);
    }
}
