package com.neram.pos.controller;

import com.neram.pos.dto.InventoryItemDTO;
import com.neram.pos.dto.InventoryItemRequestDTO;
import com.neram.pos.dto.InventoryTransactionDTO;
import com.neram.pos.dto.StockDecreaseRequestDTO;
import com.neram.pos.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<List<InventoryItemDTO>> getAllItems(@RequestParam(required = false) String category) {
        return ResponseEntity.ok(inventoryService.getAllItems(category));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<List<InventoryItemDTO>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<InventoryItemDTO> getItemById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getItemById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<InventoryItemDTO> createItem(@RequestBody InventoryItemRequestDTO request) {
        return ResponseEntity.ok(inventoryService.createItem(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<InventoryItemDTO> updateItem(@PathVariable Long id, @RequestBody InventoryItemRequestDTO request) {
        return ResponseEntity.ok(inventoryService.updateItem(id, request));
    }

    @PostMapping("/{id}/decrease")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<InventoryItemDTO> decreaseStock(
            @PathVariable Long id,
            @RequestBody StockDecreaseRequestDTO request,
            Authentication auth
    ) {
        String username = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(inventoryService.decreaseStock(id, request.getQuantity(), request.getReason(), username));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<InventoryItemDTO> updateStock(
            @PathVariable Long id,
            @RequestBody Map<String, Double> payload,
            Authentication auth
    ) {
        Double change = payload.getOrDefault("change", 0.0);
        String username = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(inventoryService.updateStock(id, change, username));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<InventoryTransactionDTO>> getTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) Long supplierId
    ) {
        return ResponseEntity.ok(inventoryService.getTransactions(startDate, endDate, userId, itemId, transactionType, supplierId));
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<InventoryTransactionDTO>> getItemTransactions(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getTransactions(null, null, null, id, null, null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        inventoryService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
