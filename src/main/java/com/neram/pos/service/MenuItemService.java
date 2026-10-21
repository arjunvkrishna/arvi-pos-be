package com.neram.pos.service;

import com.neram.pos.dto.MenuItemDTO;
import com.neram.pos.dto.MenuItemRequestDTO;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.repository.CategoryRepository;
import com.neram.pos.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    public List<MenuItemDTO> getAllMenuItems() {
        Map<Long, String> catMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        return menuItemRepository.findByActiveTrue().stream()
                .map(m -> toDTO(m, catMap.getOrDefault(m.getCategoryId(), "General")))
                .collect(Collectors.toList());
    }

    public List<MenuItemDTO> getAvailableMenuItems() {
        Map<Long, String> catMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        return menuItemRepository.findByIsAvailableTrueAndActiveTrue().stream()
                .map(m -> toDTO(m, catMap.getOrDefault(m.getCategoryId(), "General")))
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public MenuItemDTO createMenuItem(MenuItemRequestDTO dto) {
        MenuItem item = new MenuItem();
        item.setName(dto.getName());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(dto.getPrice());
        item.setDescription(dto.getDescription());
        item.setImageUrl(dto.getImageUrl());
        item.setIsVeg(dto.getIsVeg() != null ? dto.getIsVeg() : true);
        item.setIsAvailable(dto.getIsAvailable() != null ? dto.getIsAvailable() : true);
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        MenuItem saved = menuItemRepository.save(item);
        auditLogService.log("CREATE", "MENU_ITEM", saved.getId(), null, saved.getName() + " - ₹" + saved.getPrice());
        String catName = categoryRepository.findById(saved.getCategoryId()).map(Category::getName).orElse("General");
        return toDTO(saved, catName);
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public MenuItemDTO updateMenuItem(Long id, MenuItemRequestDTO dto) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found with id: " + id));

        String oldVal = item.getName() + " - ₹" + item.getPrice();
        item.setName(dto.getName());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(dto.getPrice());
        item.setDescription(dto.getDescription());
        item.setImageUrl(dto.getImageUrl());
        if (dto.getIsVeg() != null) item.setIsVeg(dto.getIsVeg());
        if (dto.getIsAvailable() != null) item.setIsAvailable(dto.getIsAvailable());
        if (dto.getActive() != null) item.setActive(dto.getActive());
        item.setUpdatedAt(LocalDateTime.now());

        MenuItem updated = menuItemRepository.save(item);
        auditLogService.log("UPDATE", "MENU_ITEM", updated.getId(), oldVal, updated.getName() + " - ₹" + updated.getPrice());
        String catName = categoryRepository.findById(updated.getCategoryId()).map(Category::getName).orElse("General");
        return toDTO(updated, catName);
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public MenuItemDTO toggleAvailability(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found with id: " + id));
        item.setIsAvailable(!item.getIsAvailable());
        item.setUpdatedAt(LocalDateTime.now());
        MenuItem updated = menuItemRepository.save(item);
        auditLogService.log("TOGGLE_AVAILABILITY", "MENU_ITEM", id, null, "Available: " + updated.getIsAvailable());
        String catName = categoryRepository.findById(updated.getCategoryId()).map(Category::getName).orElse("General");
        return toDTO(updated, catName);
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public void deleteMenuItem(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found with id: " + id));
        item.setActive(false);
        item.setUpdatedAt(LocalDateTime.now());
        menuItemRepository.save(item);
        auditLogService.log("DELETE", "MENU_ITEM", id, item.getName(), "Deactivated");
    }

    public MenuItemDTO toDTO(MenuItem m, String categoryName) {
        return MenuItemDTO.builder()
                .id(m.getId())
                .name(m.getName())
                .categoryId(m.getCategoryId())
                .categoryName(categoryName)
                .price(m.getPrice())
                .description(m.getDescription())
                .imageUrl(m.getImageUrl())
                .isVeg(m.getIsVeg())
                .isAvailable(m.getIsAvailable())
                .active(m.getActive())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
