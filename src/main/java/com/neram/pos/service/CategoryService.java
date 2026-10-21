package com.neram.pos.service;

import com.neram.pos.dto.CategoryDTO;
import com.neram.pos.dto.CategoryRequestDTO;
import com.neram.pos.entity.Category;
import com.neram.pos.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final java.util.regex.Pattern ICON_CLASS = java.util.regex.Pattern.compile("^fa-[a-z0-9-]{1,40}$");
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int MAX_SORT_ORDER = 9999;

    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    /** Trims and validates a category name; it must be unique (case-insensitive) apart from the category being edited. */
    private String validName(String raw, Long selfId) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Category name is required.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Category name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        categoryRepository.findAllByNameIgnoreCase(name).stream()
                .filter(existing -> !existing.getId().equals(selfId))
                .findFirst()
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("A category named '" + existing.getName() + "' already exists"
                            + (Boolean.FALSE.equals(existing.getActive()) ? " (it was removed earlier)." : "."));
                });
        return name;
    }

    private String validDescription(String raw) {
        String d = raw == null ? null : raw.trim();
        if (d != null && d.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return d == null || d.isEmpty() ? null : d;
    }

    /** Icons are Font Awesome class names such as fa-utensils; anything else is rejected. */
    private String validIcon(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String icon = raw.trim();
        if (!ICON_CLASS.matcher(icon).matches()) {
            throw new IllegalArgumentException("Icon must be a Font Awesome class such as fa-utensils.");
        }
        return icon;
    }

    private Integer validSortOrder(Integer raw) {
        if (raw != null && (raw < 0 || raw > MAX_SORT_ORDER)) {
            throw new IllegalArgumentException("Sort order must be between 0 and " + MAX_SORT_ORDER + ".");
        }
        return raw;
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public CategoryDTO createCategory(CategoryRequestDTO dto) {
        Category cat = new Category();
        cat.setName(validName(dto.getName(), null));
        cat.setDescription(validDescription(dto.getDescription()));
        String icon = validIcon(dto.getIcon());
        cat.setIcon(icon != null ? icon : "fa-utensils");
        Integer sortOrder = validSortOrder(dto.getSortOrder());
        cat.setSortOrder(sortOrder != null ? sortOrder : 0);
        cat.setActive(true);
        cat.setCreatedAt(LocalDateTime.now());

        Category saved = categoryRepository.save(cat);
        auditLogService.log("CREATE", "CATEGORY", saved.getId(), null, saved.getName());
        return toDTO(saved);
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public CategoryDTO updateCategory(Long id, CategoryRequestDTO dto) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        String before = describe(cat);
        cat.setName(validName(dto.getName(), id));
        cat.setDescription(validDescription(dto.getDescription()));
        String icon = validIcon(dto.getIcon());
        if (icon != null) cat.setIcon(icon);
        Integer sortOrder = validSortOrder(dto.getSortOrder());
        if (sortOrder != null) cat.setSortOrder(sortOrder);
        if (dto.getActive() != null) cat.setActive(dto.getActive());

        Category updated = categoryRepository.save(cat);
        auditLogService.log("UPDATE", "CATEGORY", updated.getId(), before, describe(updated));
        return toDTO(updated);
    }

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public void deleteCategory(Long id) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        cat.setActive(false);
        categoryRepository.save(cat);
        auditLogService.log("DELETE", "CATEGORY", id, cat.getName(), "Deactivated");
    }

    private static String describe(Category c) {
        return String.format("%s | icon=%s | order=%s | active=%s | %s", c.getName(), c.getIcon(), c.getSortOrder(), c.getActive(),
                c.getDescription() == null ? "(no description)" : c.getDescription());
    }

    public CategoryDTO toDTO(Category c) {
        return CategoryDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .icon(c.getIcon())
                .sortOrder(c.getSortOrder())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
