package com.neram.pos.controller;

import com.neram.pos.dto.TipPoolCalculateRequestDTO;
import com.neram.pos.dto.TipPoolDistributionDTO;
import com.neram.pos.dto.TipPoolEmployeeShareDTO;
import com.neram.pos.service.TipPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/shifts/tip-pool")
@RequiredArgsConstructor
@com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.SCHEDULING)
public class TipPoolController {

    private final TipPoolService tipPoolService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TipPoolDistributionDTO> getDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tipPoolService.getDistribution(date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TipPoolDistributionDTO> calculateTipPool(@RequestBody(required = false) TipPoolCalculateRequestDTO request) {
        return ResponseEntity.ok(tipPoolService.calculateAndDistribute(request, null));
    }

    @PostMapping("/shares/{shareId}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TipPoolEmployeeShareDTO> markSharePaid(@PathVariable Long shareId) {
        return ResponseEntity.ok(tipPoolService.markSharePaid(shareId));
    }
}
