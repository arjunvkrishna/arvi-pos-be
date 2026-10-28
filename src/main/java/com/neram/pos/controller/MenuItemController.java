package com.neram.pos.controller;

import com.neram.pos.dto.MenuItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.neram.pos.dto.MenuItemRequestDTO;
import com.neram.pos.dto.MenuPriceImportDTO;
import com.neram.pos.service.MenuPriceImportService;
import com.neram.pos.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;
    private final MenuPriceImportService menuPriceImportService;
    private final com.neram.pos.service.MenuVersionService menuVersionService;

    @GetMapping
    public ResponseEntity<List<MenuItemDTO>> getAllMenuItems(
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return PagedResponse.of(menuItemService.searchMenuItems(availability, q, pageable));
    }

    /** Changes whenever anything about the menu changes; apps check it every few seconds and reload the menu only then. */
    @GetMapping("/version")
    public ResponseEntity<java.util.Map<String, String>> menuVersion() {
        return ResponseEntity.ok(java.util.Map.of("version", menuVersionService.current()));
    }

    @GetMapping("/available")
    public ResponseEntity<List<MenuItemDTO>> getAvailableMenuItems() {
        return ResponseEntity.ok(menuItemService.getAvailableMenuItems());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<MenuItemDTO> createMenuItem(@RequestBody MenuItemRequestDTO request) {
        return ResponseEntity.ok(menuItemService.createMenuItem(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<MenuItemDTO> updateMenuItem(@PathVariable Long id, @RequestBody MenuItemRequestDTO request) {
        return ResponseEntity.ok(menuItemService.updateMenuItem(id, request));
    }

    @PatchMapping("/{id}/toggle-availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemDTO> toggleAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.toggleAvailability(id));
    }

    /** The caller's branch's own price and/or availability for this item (needs a branch selected). */
    @PutMapping("/{id}/branch")
    @PreAuthorize("hasAuthority('PERM_MANAGE_OUTLET_SETTINGS')")
    public ResponseEntity<MenuItemDTO> updateBranchSetting(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean clearPrice = body.containsKey("price") && body.get("price") == null;
        java.math.BigDecimal price = body.get("price") == null ? null : new java.math.BigDecimal(String.valueOf(body.get("price")));
        Boolean available = body.get("isAvailable") == null ? null : Boolean.valueOf(String.valueOf(body.get("isAvailable")));
        return ResponseEntity.ok(menuItemService.updateBranchSetting(id, price, clearPrice, available));
    }

    /** Back to the cafe-wide price and availability for the caller's branch. */
    @DeleteMapping("/{id}/branch")
    @PreAuthorize("hasAuthority('PERM_MANAGE_OUTLET_SETTINGS')")
    public ResponseEntity<MenuItemDTO> resetBranchSetting(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.resetBranchSetting(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable Long id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.noContent().build();
    }

    /** Whole active menu as a JSON document that can be edited and re-imported. */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> exportMenu() {
        return ResponseEntity.ok(menuPriceImportService.export());
    }

    /** Dry run: what an uploaded file would change. Writes nothing. */
    @PostMapping("/import/preview")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<MenuPriceImportDTO.Result> previewImport(@RequestBody JsonNode body) {
        return ResponseEntity.ok(menuPriceImportService.preview(body));
    }

    /** Applies the price changes in an uploaded file (prices only, listed dishes only). */
    @PostMapping("/import/apply")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<MenuPriceImportDTO.Result> applyImport(@RequestBody JsonNode body) {
        return ResponseEntity.ok(menuPriceImportService.apply(body));
    }
}
