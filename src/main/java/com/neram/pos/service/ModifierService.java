package com.neram.pos.service;

import com.neram.pos.dto.ModifierGroupDTO;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.MenuItemModifierGroup;
import com.neram.pos.entity.Modifier;
import com.neram.pos.entity.ModifierGroup;
import com.neram.pos.repository.MenuItemModifierGroupRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.repository.ModifierGroupRepository;
import com.neram.pos.repository.ModifierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModifierService {

    private final ModifierGroupRepository groupRepository;
    private final ModifierRepository modifierRepository;
    private final MenuItemModifierGroupRepository menuItemModifierGroupRepository;
    private final MenuItemRepository menuItemRepository;

    public List<ModifierGroupDTO> getAllActiveGroups() {
        return groupRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ModifierGroupDTO> getGroupsForMenuItem(Long menuItemId) {
        List<MenuItemModifierGroup> links = menuItemModifierGroupRepository.findByMenuItemId(menuItemId);
        List<Long> groupIds = links.stream().map(MenuItemModifierGroup::getModifierGroupId).collect(Collectors.toList());
        return groupRepository.findAllById(groupIds).stream()
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private static void validate(ModifierGroupDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Modifier group name is required.");
        }
        int min = dto.getMinSelections() != null ? dto.getMinSelections() : 0;
        int max = dto.getMaxSelections() != null ? dto.getMaxSelections() : 1;
        if (min < 0 || max < 0) throw new IllegalArgumentException("Selections cannot be negative.");
        if (max > 0 && min > max) throw new IllegalArgumentException("Minimum selections cannot be more than the maximum.");
        if (dto.getModifiers() != null) {
            for (ModifierGroupDTO.ModifierDTO m : dto.getModifiers()) {
                if (m.getName() == null || m.getName().isBlank()) throw new IllegalArgumentException("Every option needs a name.");
                if (m.getInventoryQuantity() != null && m.getInventoryQuantity().signum() < 0) throw new IllegalArgumentException("Stock used per option cannot be negative.");
                if (m.getInventoryQuantity() != null && m.getInventoryQuantity().signum() > 0 && m.getInventoryItemId() == null) {
                    throw new IllegalArgumentException("Pick the stock item that \"" + m.getName().trim() + "\" uses.");
                }
            }
        }
    }

    @Transactional
    public ModifierGroupDTO createGroup(ModifierGroupDTO dto) {
        validate(dto);
        ModifierGroup group = ModifierGroup.builder()
                .name(dto.getName().trim())
                .description(dto.getDescription())
                .minSelections(dto.getMinSelections() != null ? dto.getMinSelections() : 0)
                .maxSelections(dto.getMaxSelections() != null ? dto.getMaxSelections() : 1)
                .isActive(true)
                .build();
        ModifierGroup saved = groupRepository.save(group);

        if (dto.getModifiers() != null) {
            for (ModifierGroupDTO.ModifierDTO mDto : dto.getModifiers()) {
                Modifier mod = Modifier.builder()
                        .group(saved)
                        .name(mDto.getName().trim())
                        .priceAdjustment(mDto.getPriceAdjustment() != null ? mDto.getPriceAdjustment() : BigDecimal.ZERO)
                        .isDefault(Boolean.TRUE.equals(mDto.getIsDefault()))
                        .isActive(true)
                        .sortOrder(mDto.getSortOrder() != null ? mDto.getSortOrder() : 0)
                        .inventoryItemId(mDto.getInventoryItemId())
                        .inventoryQuantity(mDto.getInventoryQuantity() != null ? mDto.getInventoryQuantity() : BigDecimal.ZERO)
                        .build();
                saved.getModifiers().add(mod);
            }
            saved = groupRepository.save(saved);
        }
        return toDTO(saved);
    }

    @Transactional
    public ModifierGroupDTO updateGroup(Long id, ModifierGroupDTO dto) {
        validate(dto);
        ModifierGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Modifier group not found: " + id));
        group.setName(dto.getName().trim());
        group.setDescription(dto.getDescription());
        group.setMinSelections(dto.getMinSelections() != null ? dto.getMinSelections() : 0);
        group.setMaxSelections(dto.getMaxSelections() != null ? dto.getMaxSelections() : 1);
        if (dto.getIsActive() != null) group.setIsActive(dto.getIsActive());

        // Update the options in place: an option keeps its id (bills already rung up point at it), a removed one is switched off, not deleted
        if (dto.getModifiers() != null) {
            java.util.Map<Long, Modifier> existing = new java.util.HashMap<>();
            for (Modifier m : group.getModifiers()) {
                if (m.getId() != null) existing.put(m.getId(), m);
            }
            java.util.Set<Long> kept = new java.util.HashSet<>();
            for (ModifierGroupDTO.ModifierDTO mDto : dto.getModifiers()) {
                Modifier mod = mDto.getId() != null ? existing.get(mDto.getId()) : null;
                if (mod == null) {
                    mod = Modifier.builder().group(group).build();
                    group.getModifiers().add(mod);
                } else {
                    kept.add(mod.getId());
                }
                mod.setName(mDto.getName().trim());
                mod.setPriceAdjustment(mDto.getPriceAdjustment() != null ? mDto.getPriceAdjustment() : BigDecimal.ZERO);
                mod.setIsDefault(Boolean.TRUE.equals(mDto.getIsDefault()));
                mod.setIsActive(mDto.getIsActive() == null || mDto.getIsActive());
                mod.setSortOrder(mDto.getSortOrder() != null ? mDto.getSortOrder() : 0);
                mod.setInventoryItemId(mDto.getInventoryItemId());
                mod.setInventoryQuantity(mDto.getInventoryQuantity() != null ? mDto.getInventoryQuantity() : BigDecimal.ZERO);
            }
            for (Modifier m : existing.values()) {
                if (!kept.contains(m.getId())) m.setIsActive(false);
            }
        }
        return toDTO(groupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(Long id) {
        groupRepository.findById(id).ifPresent(g -> {
            g.setIsActive(false);
            groupRepository.save(g);
        });
    }

    @Transactional
    public void assignGroupsToMenuItem(Long menuItemId, List<Long> modifierGroupIds) {
        if (!menuItemRepository.existsById(menuItemId)) {
            throw new IllegalArgumentException("Menu item not found: " + menuItemId);
        }
        menuItemModifierGroupRepository.deleteByMenuItemId(menuItemId);
        if (modifierGroupIds != null) {
            for (Long gId : modifierGroupIds) {
                menuItemModifierGroupRepository.save(new MenuItemModifierGroup(menuItemId, gId));
            }
        }
    }

    public ModifierGroupDTO toDTO(ModifierGroup g) {
        List<Modifier> mods = modifierRepository.findByGroupIdAndIsActiveTrueOrderBySortOrderAsc(g.getId());
        List<ModifierGroupDTO.ModifierDTO> mDtos = mods.stream().map(m -> ModifierGroupDTO.ModifierDTO.builder()
                .id(m.getId())
                .groupId(g.getId())
                .name(m.getName())
                .priceAdjustment(m.getPriceAdjustment())
                .isDefault(m.getIsDefault())
                .isActive(m.getIsActive())
                .sortOrder(m.getSortOrder())
                .inventoryItemId(m.getInventoryItemId())
                .inventoryQuantity(m.getInventoryQuantity())
                .build()).collect(Collectors.toList());

        return ModifierGroupDTO.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .minSelections(g.getMinSelections())
                .maxSelections(g.getMaxSelections())
                .isActive(g.getIsActive())
                .modifiers(mDtos)
                .build();
    }
}
