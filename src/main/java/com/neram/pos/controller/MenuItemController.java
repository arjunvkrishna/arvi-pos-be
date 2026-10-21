package com.neram.pos.controller;

import com.neram.pos.dto.MenuItemDTO;
import com.neram.pos.dto.MenuItemRequestDTO;
import com.neram.pos.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @GetMapping
    public ResponseEntity<List<MenuItemDTO>> getAllMenuItems() {
        return ResponseEntity.ok(menuItemService.getAllMenuItems());
    }

    @GetMapping("/available")
    public ResponseEntity<List<MenuItemDTO>> getAvailableMenuItems() {
        return ResponseEntity.ok(menuItemService.getAvailableMenuItems());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemDTO> createMenuItem(@RequestBody MenuItemRequestDTO request) {
        return ResponseEntity.ok(menuItemService.createMenuItem(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemDTO> updateMenuItem(@PathVariable Long id, @RequestBody MenuItemRequestDTO request) {
        return ResponseEntity.ok(menuItemService.updateMenuItem(id, request));
    }

    @PatchMapping("/{id}/toggle-availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MenuItemDTO> toggleAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.toggleAvailability(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable Long id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.noContent().build();
    }
}
