package com.neram.pos.controller;

import com.neram.pos.dto.StockOpsDTO;
import com.neram.pos.service.StockCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Physical stock counts. People with stock access may count; only a manager or admin may book the differences. */
@RestController
@RequestMapping("/api/inventory/counts")
@RequiredArgsConstructor
public class StockCountController {

    private final StockCountService service;

    private static String who(Authentication a) { return a != null ? a.getName() : null; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<List<StockOpsDTO.Count>> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<StockOpsDTO.Count> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<StockOpsDTO.Count> start(@RequestBody(required = false) StockOpsDTO.StartCountRequest body, Authentication auth) {
        return ResponseEntity.ok(service.start(body != null ? body.getCategory() : null, body != null ? body.getNote() : null, who(auth)));
    }

    @PutMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<StockOpsDTO.Count> save(@PathVariable Long id, @RequestBody StockOpsDTO.SaveLines body) {
        return ResponseEntity.ok(service.saveLines(id, body.getLines()));
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<StockOpsDTO.Count> apply(@PathVariable Long id, Authentication auth) { return ResponseEntity.ok(service.apply(id, who(auth))); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<StockOpsDTO.Count> cancel(@PathVariable Long id, Authentication auth) { return ResponseEntity.ok(service.cancel(id, who(auth))); }
}
