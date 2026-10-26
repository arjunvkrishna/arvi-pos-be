package com.neram.pos.controller;

import com.neram.pos.dto.InventoryItemDTO;
import com.neram.pos.dto.InventoryItemRequestDTO;
import com.neram.pos.dto.InventoryTransactionDTO;
import com.neram.pos.dto.StockDecreaseRequestDTO;
import com.neram.pos.service.InventoryService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
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
    private final com.neram.pos.service.StockOpsService stockOps;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<List<InventoryItemDTO>> getAllItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean lowOnly,
            Pageable pageable) {
        return PagedResponse.of(inventoryService.searchItems(category, q, lowOnly, pageable));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<List<InventoryItemDTO>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    /** Stock that is expired or about to expire (?all=true lists every batch with an expiry), earliest first. */
    @GetMapping("/expiry")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<List<com.neram.pos.dto.ExpiryBatchDTO>> getExpiry(@RequestParam(defaultValue = "false") boolean all) {
        return ResponseEntity.ok(inventoryService.getExpiryBatches(all));
    }

    /** What to buy: items that are out, below their minimum or will run out within a week at the recent pace, with a suggested quantity and supplier. */
    @GetMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_MANAGE_INVENTORY')")
    public ResponseEntity<com.neram.pos.dto.StockOpsDTO.Reorder> getReorder() {
        return ResponseEntity.ok(stockOps.reorder());
    }

    /** Ingredient prices that moved between the last two purchases, and dishes whose margin is under the floor. */
    @GetMapping("/cost-alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<com.neram.pos.dto.StockOpsDTO.CostAlerts> getCostAlerts() {
        return ResponseEntity.ok(stockOps.costAlerts());
    }

    @GetMapping("/cost-alerts/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<com.neram.pos.dto.StockOpsDTO.CostAlertSettings> getCostAlertSettings() {
        return ResponseEntity.ok(stockOps.getSettings());
    }

    @PutMapping("/cost-alerts/settings")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<com.neram.pos.dto.StockOpsDTO.CostAlertSettings> saveCostAlertSettings(@RequestBody com.neram.pos.dto.StockOpsDTO.CostAlertSettings body, Authentication auth) {
        return ResponseEntity.ok(stockOps.saveSettings(body, auth != null ? auth.getName() : null));
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
        return ResponseEntity.ok(inventoryService.decreaseStock(id, request.getQuantity(), request.getReason(), username, Boolean.TRUE.equals(request.getInPacks()), request.getReasonType(), Boolean.TRUE.equals(request.getAllowNegative())));
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
            @RequestParam(required = false) Long supplierId,
            Pageable pageable
    ) {
        return PagedResponse.of(inventoryService.getTransactions(startDate, endDate, userId, itemId, transactionType, supplierId, pageable));
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
