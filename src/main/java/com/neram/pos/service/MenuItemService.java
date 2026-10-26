package com.neram.pos.service;

import com.neram.pos.dto.MenuItemDTO;
import com.neram.pos.dto.MenuItemRequestDTO;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.MenuItemBranchSetting;
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
    private final MenuBranchService menuBranchService;

    public List<MenuItemDTO> getAllMenuItems() {
        Map<Long, String> catMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        Map<Long, MenuItemBranchSetting> overrides = menuBranchService.overrides();
        return menuItemRepository.findByActiveTrue().stream()
                .map(m -> toDTO(m, catMap.getOrDefault(m.getCategoryId(), "General"), overrides))
                .collect(Collectors.toList());
    }

    /**
     * One page of the active menu items (ordered by id) for the Menu & Pricing table. Optional filters: availability
     * ("available" / "unavailable"; anything else means all) and a text search over name, category, description and the
     * veg / non-veg label.
     */
    public org.springframework.data.domain.Page<MenuItemDTO> searchMenuItems(String availability, String q, org.springframework.data.domain.Pageable pageable) {
        Map<Long, String> catMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        String text = q == null ? "" : q.trim().toLowerCase();
        String pattern = com.neram.pos.util.LikeSearch.pattern(q);
        List<Long> matchingCategoryIds = text.isEmpty() ? List.of() : catMap.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().toLowerCase().contains(text)).map(Map.Entry::getKey).collect(Collectors.toList());

        Long branchId = menuBranchService.branch().orElse(null);
        Map<Long, MenuItemBranchSetting> overrides = menuBranchService.overrides();
        org.springframework.data.jpa.domain.Specification<MenuItem> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new java.util.ArrayList<>();
            p.add(cb.isTrue(root.get("active")));
            if ("available".equalsIgnoreCase(availability) || "unavailable".equalsIgnoreCase(availability)) {
                // The branch's own setting wins over the cafe-wide one.
                jakarta.persistence.criteria.Expression<Boolean> effective = root.get("isAvailable");
                if (branchId != null) {
                    jakarta.persistence.criteria.Subquery<Boolean> own = query.subquery(Boolean.class);
                    jakarta.persistence.criteria.Root<MenuItemBranchSetting> b = own.from(MenuItemBranchSetting.class);
                    own.select(b.<Boolean>get("isAvailable")).where(
                            cb.equal(b.get("menuItemId"), root.get("id")), cb.equal(b.get("outletId"), branchId));
                    effective = cb.coalesce(own, root.<Boolean>get("isAvailable"));
                }
                p.add("available".equalsIgnoreCase(availability) ? cb.isTrue(effective) : cb.isFalse(effective));
            }
            if (pattern != null) {
                List<jakarta.persistence.criteria.Predicate> any = new java.util.ArrayList<>();
                any.add(com.neram.pos.util.LikeSearch.contains(cb, root.get("name"), pattern));
                any.add(com.neram.pos.util.LikeSearch.contains(cb, root.get("description"), pattern));
                if (!matchingCategoryIds.isEmpty()) any.add(root.get("categoryId").in(matchingCategoryIds));
                // An item whose category is unknown is shown as "General"
                if ("general".contains(text)) any.add(catMap.isEmpty() ? cb.conjunction() : cb.not(root.get("categoryId").in(catMap.keySet())));
                // The screen also matches the words "veg" (veg items) and "non-veg" / "nonveg" (the others)
                if ("veg".contains(text)) any.add(cb.isTrue(root.get("isVeg")));
                if ("non-veg nonveg".contains(text)) any.add(cb.isFalse(root.get("isVeg")));
                p.add(cb.or(any.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }
            return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return menuItemRepository.findAll(spec, com.neram.pos.util.PagedResponse.withSort(pageable, org.springframework.data.domain.Sort.by("id")))
                .map(m -> toDTO(m, catMap.getOrDefault(m.getCategoryId(), "General"), overrides));
    }

    public List<MenuItemDTO> getAvailableMenuItems() {
        Map<Long, String> catMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        Map<Long, MenuItemBranchSetting> overrides = menuBranchService.overrides();
        // Active items, filtered by what is available in the caller's own branch.
        return menuItemRepository.findByActiveTrue().stream()
                .filter(m -> MenuBranchService.availableIn(m, overrides))
                .map(m -> toDTO(m, catMap.getOrDefault(m.getCategoryId(), "General"), overrides))
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

    /**
     * Sold out / available. In a branch this changes that branch only; in the whole-cafe view (owner, main manager
     * with no branch picked) it changes the cafe-wide default that every branch without its own setting follows.
     */
    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public MenuItemDTO toggleAvailability(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found with id: " + id));
        String actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName() : null;
        if (menuBranchService.branch().isPresent()) {
            boolean now = menuBranchService.toggleAvailability(item, actor);
            auditLogService.log("TOGGLE_AVAILABILITY", "MENU_ITEM", id, null, "Branch " + menuBranchService.branch().get() + " available: " + now);
        } else {
            item.setIsAvailable(!item.getIsAvailable());
            item.setUpdatedAt(LocalDateTime.now());
            menuItemRepository.save(item);
            auditLogService.log("TOGGLE_AVAILABILITY", "MENU_ITEM", id, null, "Cafe-wide available: " + item.getIsAvailable());
        }
        String catName = categoryRepository.findById(item.getCategoryId()).map(Category::getName).orElse("General");
        return toDTO(item, catName, menuBranchService.overrides());
    }

    /** This branch's own price/availability for an item. Needs a branch selected. */
    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public MenuItemDTO updateBranchSetting(Long id, java.math.BigDecimal price, boolean clearPrice, Boolean available) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found with id: " + id));
        String actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName() : null;
        menuBranchService.update(item, price, clearPrice, available, actor);
        auditLogService.log("BRANCH_MENU_SETTING", "MENU_ITEM", id, null,
                "Branch " + menuBranchService.branch().orElse(null) + " price=" + (clearPrice ? "cafe-wide" : price) + " available=" + available);
        String catName = categoryRepository.findById(item.getCategoryId()).map(Category::getName).orElse("General");
        return toDTO(item, catName, menuBranchService.overrides());
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public MenuItemDTO resetBranchSetting(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found with id: " + id));
        menuBranchService.reset(id);
        String catName = categoryRepository.findById(item.getCategoryId()).map(Category::getName).orElse("General");
        return toDTO(item, catName, menuBranchService.overrides());
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
        return toDTO(m, categoryName, Map.of());
    }

    /** The item as one branch sees it: the branch's own price/availability where it set them. */
    public MenuItemDTO toDTO(MenuItem m, String categoryName, Map<Long, MenuItemBranchSetting> overrides) {
        MenuItemBranchSetting o = overrides.get(m.getId());
        return MenuItemDTO.builder()
                .id(m.getId())
                .name(m.getName())
                .categoryId(m.getCategoryId())
                .categoryName(categoryName)
                .price(MenuBranchService.priceOf(m, overrides))
                .description(m.getDescription())
                .imageUrl(m.getImageUrl())
                .isVeg(m.getIsVeg())
                .isAvailable(o != null && o.getIsAvailable() != null ? o.getIsAvailable() : m.getIsAvailable())
                .active(m.getActive())
                .createdAt(m.getCreatedAt())
                .basePrice(m.getPrice())
                .baseAvailable(m.getIsAvailable())
                .branchOverride(o != null)
                .build();
    }
}
