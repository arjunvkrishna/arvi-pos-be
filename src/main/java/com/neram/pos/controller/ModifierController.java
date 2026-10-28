package com.neram.pos.controller;

import com.neram.pos.dto.ModifierGroupDTO;
import com.neram.pos.service.ModifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ModifierController {

    private final ModifierService modifierService;

    @GetMapping("/modifier-groups")
    public ResponseEntity<List<ModifierGroupDTO>> getAllActiveGroups() {
        return ResponseEntity.ok(modifierService.getAllActiveGroups());
    }

    @GetMapping("/menu/{menuItemId}/modifier-groups")
    public ResponseEntity<List<ModifierGroupDTO>> getGroupsForMenuItem(@PathVariable Long menuItemId) {
        return ResponseEntity.ok(modifierService.getGroupsForMenuItem(menuItemId));
    }

    @PostMapping("/modifier-groups")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<ModifierGroupDTO> createGroup(@RequestBody ModifierGroupDTO dto) {
        return ResponseEntity.ok(modifierService.createGroup(dto));
    }

    @PutMapping("/modifier-groups/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<ModifierGroupDTO> updateGroup(@PathVariable Long id, @RequestBody ModifierGroupDTO dto) {
        return ResponseEntity.ok(modifierService.updateGroup(id, dto));
    }

    @DeleteMapping("/modifier-groups/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        modifierService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/menu/{menuItemId}/modifier-groups")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<Void> assignGroups(@PathVariable Long menuItemId, @RequestBody List<Long> modifierGroupIds) {
        modifierService.assignGroupsToMenuItem(menuItemId, modifierGroupIds);
        return ResponseEntity.ok().build();
    }
}
