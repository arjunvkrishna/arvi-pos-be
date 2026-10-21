package com.neram.pos.controller;

import com.neram.pos.entity.Outlet;
import com.neram.pos.service.OutletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OutletController {

    private final OutletService outletService;

    @GetMapping("/outlets")
    public ResponseEntity<List<Outlet>> getOutlets() {
        return ResponseEntity.ok(outletService.getOutletsForCurrentTenant());
    }

    @PostMapping("/outlets")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CAFE_ADMIN')")
    public ResponseEntity<?> createOutlet(@RequestBody Outlet outlet) {
        try {
            return ResponseEntity.ok(outletService.createOutlet(outlet));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/outlets/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CAFE_ADMIN')")
    public ResponseEntity<?> updateOutlet(@PathVariable Long id, @RequestBody Outlet outlet) {
        try {
            return ResponseEntity.ok(outletService.updateOutlet(id, outlet));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/franchise/analytics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CAFE_ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> getFranchiseAnalytics() {
        return ResponseEntity.ok(outletService.getFranchiseAnalytics());
    }
}
