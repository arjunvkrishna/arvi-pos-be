package com.neram.pos.service;

import com.neram.pos.dto.PublicMenuCategoryDTO;
import com.neram.pos.dto.PublicMenuItemDTO;
import com.neram.pos.dto.PublicMenuResponseDTO;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.repository.CategoryRepository;
import com.neram.pos.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicMenuService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final AppSettingService appSettingService;

    public boolean isPublicMenuEnabled() {
        return appSettingService.isPublicMenuEnabled();
    }

    @Cacheable(value = "publicMenu", key = "'activeMenu'")
    public PublicMenuResponseDTO getPublicMenu() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderBySortOrderAsc();
        List<MenuItem> allActiveItems = menuItemRepository.findByActiveTrue();

        Map<Long, List<MenuItem>> itemsByCat = allActiveItems.stream()
                .collect(Collectors.groupingBy(MenuItem::getCategoryId));

        List<PublicMenuCategoryDTO> categoryDTOs = categories.stream()
                .map(cat -> {
                    List<MenuItem> catItems = itemsByCat.getOrDefault(cat.getId(), Collections.emptyList());
                    List<PublicMenuItemDTO> itemDTOs = catItems.stream()
                            .map(m -> PublicMenuItemDTO.builder()
                                    .id(m.getId())
                                    .name(m.getName())
                                    .price(m.getPrice())
                                    .description(m.getDescription())
                                    .imageUrl(m.getImageUrl())
                                    .isVeg(m.getIsVeg())
                                    .isAvailable(m.getIsAvailable())
                                    .build())
                            .sorted(Comparator.comparing(PublicMenuItemDTO::getName))
                            .collect(Collectors.toList());

                    return PublicMenuCategoryDTO.builder()
                            .id(cat.getId())
                            .name(cat.getName())
                            .icon(cat.getIcon())
                            .sortOrder(cat.getSortOrder())
                            .items(itemDTOs)
                            .build();
                })
                .filter(cat -> !cat.getItems().isEmpty())
                .collect(Collectors.toList());

        return PublicMenuResponseDTO.builder()
                .enabled(true)
                .message("Success")
                .categories(categoryDTOs)
                .build();
    }
}
