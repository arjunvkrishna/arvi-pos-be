package com.neram.pos.controller;

import com.neram.pos.dto.CustomerDTO;
import com.neram.pos.dto.LoyaltyLedgerDTO;
import com.neram.pos.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getCustomers(@RequestParam(required = false) String phone, Pageable pageable) {
        if (phone != null && !phone.isBlank()) {
            return customerService.findByPhone(phone)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.ok(customerService.listCustomers(pageable));
    }

    @GetMapping("/customers/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CustomerDTO>> searchCustomers(@RequestParam String q) {
        return ResponseEntity.ok(customerService.search(q));
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CustomerDTO> getCustomer(@PathVariable Long id) {
        return customerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customers/{id}/loyalty")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<LoyaltyLedgerDTO>> getLoyaltyHistory(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getLoyaltyHistory(id));
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CustomerDTO> createCustomer(@RequestBody CustomerDTO dto) {
        return ResponseEntity.ok(customerService.createCustomer(dto));
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CustomerDTO> updateCustomer(@PathVariable Long id, @RequestBody CustomerDTO dto) {
        return ResponseEntity.ok(customerService.updateCustomer(id, dto));
    }

    @PostMapping("/public/{cafe}/customers/unsubscribe")
    public ResponseEntity<?> unsubscribe(@PathVariable String cafe, @RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token is required"));
        }
        boolean ok = customerService.unsubscribe(token.trim());
        if (!ok) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "You have been successfully unsubscribed."));
    }
}
