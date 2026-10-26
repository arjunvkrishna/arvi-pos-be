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
    private final com.neram.pos.repository.TenantRepository tenantRepository;
    private final MenuBranchService menuBranchService;

    public boolean isPublicMenuEnabled() {
        return appSettingService.isPublicMenuEnabled();
    }

    @Cacheable(value = "publicMenu", key = "T(com.neram.pos.tenancy.TenantContext).requireId() + ':' + T(com.neram.pos.access.OutletContext).currentIdOrNull()")
    public PublicMenuResponseDTO getPublicMenu() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderBySortOrderAsc();
        List<MenuItem> allActiveItems = menuItemRepository.findByActiveTrue();
        // A branch's own price and sold-out state, when the link names the branch (?branch=CODE).
        java.util.Map<Long, com.neram.pos.entity.MenuItemBranchSetting> overrides = menuBranchService.overrides();

        Map<Long, List<MenuItem>> itemsByCat = allActiveItems.stream()
                .collect(Collectors.groupingBy(MenuItem::getCategoryId));

        List<PublicMenuCategoryDTO> categoryDTOs = categories.stream()
                .map(cat -> {
                    List<MenuItem> catItems = itemsByCat.getOrDefault(cat.getId(), Collections.emptyList());
                    List<PublicMenuItemDTO> itemDTOs = catItems.stream()
                            .map(m -> PublicMenuItemDTO.builder()
                                    .id(m.getId())
                                    .name(m.getName())
                                    .price(MenuBranchService.priceOf(m, overrides))
                                    .description(m.getDescription())
                                    .imageUrl(m.getImageUrl())
                                    .isVeg(m.getIsVeg())
                                    .isAvailable(MenuBranchService.availableIn(m, overrides))
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

        com.neram.pos.entity.Tenant cafe = tenantRepository.findById(com.neram.pos.tenancy.TenantContext.requireId()).orElse(null);
        return PublicMenuResponseDTO.builder()
                .enabled(true)
                .message("Success")
                .categories(categoryDTOs)
                .cafeName(cafe != null ? cafe.getName() : null)
                .tagline(cafe != null ? cafe.getTagline() : null)
                .logoUrl(cafe != null ? cafe.getLogoUrl() : null)
                .address(cafe != null ? cafe.getAddress() : null)
                .phone(cafe != null ? cafe.getContactPhone() : null)
                .currencySymbol(cafe != null ? cafe.getCurrencySymbol() : null)
                .build();
    }
}
