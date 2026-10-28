package com.neram.pos.controller;

import com.neram.pos.dto.ComboGroupDTO;
import com.neram.pos.service.ComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ComboController {

    private final ComboService comboService;

    @GetMapping("/menu/{menuItemId}/combos")
    public ResponseEntity<List<ComboGroupDTO>> getCombosForMenuItem(@PathVariable Long menuItemId) {
        return ResponseEntity.ok(comboService.getCombosForMenuItem(menuItemId));
    }

    @PostMapping("/combo-groups")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<ComboGroupDTO> createComboGroup(@RequestBody ComboGroupDTO dto) {
        return ResponseEntity.ok(comboService.createComboGroup(dto));
    }

    @DeleteMapping("/combo-groups/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<Void> deleteComboGroup(@PathVariable Long id) {
        comboService.deleteComboGroup(id);
        return ResponseEntity.noContent().build();
    }
}
