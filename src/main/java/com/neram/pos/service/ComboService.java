package com.neram.pos.service;

import com.neram.pos.dto.ComboGroupDTO;
import com.neram.pos.entity.ComboGroup;
import com.neram.pos.entity.ComboGroupItem;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.repository.ComboGroupItemRepository;
import com.neram.pos.repository.ComboGroupRepository;
import com.neram.pos.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComboService {

    private final ComboGroupRepository comboGroupRepository;
    private final ComboGroupItemRepository comboGroupItemRepository;
    private final MenuItemRepository menuItemRepository;

    public List<ComboGroupDTO> getCombosForMenuItem(Long comboMenuItemId) {
        List<ComboGroup> groups = comboGroupRepository.findByComboMenuItemIdOrderBySortOrderAsc(comboMenuItemId);
        List<Long> itemIds = groups.stream()
                .flatMap(g -> comboGroupItemRepository.findByGroupId(g.getId()).stream())
                .map(ComboGroupItem::getMenuItemId)
                .collect(Collectors.toList());

        Map<Long, String> itemNames = menuItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, MenuItem::getName));

        return groups.stream().map(g -> {
            List<ComboGroupItem> items = comboGroupItemRepository.findByGroupId(g.getId());
            List<ComboGroupDTO.ComboItemDTO> itemDTOs = items.stream().map(ci -> ComboGroupDTO.ComboItemDTO.builder()
                    .id(ci.getId())
                    .menuItemId(ci.getMenuItemId())
                    .menuItemName(itemNames.getOrDefault(ci.getMenuItemId(), "Unknown Dish"))
                    .additionalPrice(ci.getAdditionalPrice())
                    .build()).collect(Collectors.toList());

            return ComboGroupDTO.builder()
                    .id(g.getId())
                    .comboMenuItemId(g.getComboMenuItemId())
                    .name(g.getName())
                    .minChoices(g.getMinChoices())
                    .maxChoices(g.getMaxChoices())
                    .sortOrder(g.getSortOrder())
                    .items(itemDTOs)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public ComboGroupDTO createComboGroup(ComboGroupDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) throw new IllegalArgumentException("Combo group name is required.");
        if (dto.getComboMenuItemId() == null || !menuItemRepository.existsById(dto.getComboMenuItemId())) throw new IllegalArgumentException("Pick the combo dish this group belongs to.");
        int min = dto.getMinChoices() != null ? dto.getMinChoices() : 1;
        int max = dto.getMaxChoices() != null ? dto.getMaxChoices() : 1;
        if (min < 0 || max < 1 || min > max) throw new IllegalArgumentException("Choices must be at least 0 and not above the maximum (which is at least 1).");
        if (dto.getItems() == null || dto.getItems().isEmpty()) throw new IllegalArgumentException("Add at least one dish to choose from.");
        for (ComboGroupDTO.ComboItemDTO i : dto.getItems()) {
            if (i.getMenuItemId() == null || !menuItemRepository.existsById(i.getMenuItemId())) throw new IllegalArgumentException("A dish in this group no longer exists.");
        }
        ComboGroup group = ComboGroup.builder()
                .comboMenuItemId(dto.getComboMenuItemId())
                .name(dto.getName().trim())
                .minChoices(dto.getMinChoices() != null ? dto.getMinChoices() : 1)
                .maxChoices(dto.getMaxChoices() != null ? dto.getMaxChoices() : 1)
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .build();
        ComboGroup saved = comboGroupRepository.save(group);

        if (dto.getItems() != null) {
            for (ComboGroupDTO.ComboItemDTO iDto : dto.getItems()) {
                ComboGroupItem item = ComboGroupItem.builder()
                        .group(saved)
                        .menuItemId(iDto.getMenuItemId())
                        .additionalPrice(iDto.getAdditionalPrice() != null ? iDto.getAdditionalPrice() : BigDecimal.ZERO)
                        .build();
                saved.getItems().add(item);
            }
            saved = comboGroupRepository.save(saved);
        }
        Long groupId = saved.getId();
        return getCombosForMenuItem(saved.getComboMenuItemId()).stream()
                .filter(cg -> cg.getId().equals(groupId))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void deleteComboGroup(Long id) {
        comboGroupRepository.deleteById(id);
    }
}
