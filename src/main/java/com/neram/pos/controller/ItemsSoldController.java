package com.neram.pos.controller;

import com.neram.pos.dto.ItemsSoldDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.service.ItemsSoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/analytics/items-sold")
@RequiredArgsConstructor
public class ItemsSoldController {

    private final ItemsSoldService itemsSold;

    /** What sold between two business dates (default: the last 7 days), per dish and per day or week, with search, category and order-type filters. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getItemsSold(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") String groupBy,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) List<String> orderTypes) {
        try {
            Set<Bill.OrderType> types = EnumSet.noneOf(Bill.OrderType.class);
            if (orderTypes != null) for (String t : orderTypes) types.add(Bill.OrderType.valueOf(t.trim().toUpperCase()));
            return ResponseEntity.ok(itemsSold.report(from, to, groupBy, q, categoryIds == null ? Set.of() : Set.copyOf(categoryIds), types));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "error", e.getMessage()));
        }
    }
}
