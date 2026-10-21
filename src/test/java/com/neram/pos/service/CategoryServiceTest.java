package com.neram.pos.service;

import com.neram.pos.dto.CategoryDTO;
import com.neram.pos.dto.CategoryRequestDTO;
import com.neram.pos.entity.Category;
import com.neram.pos.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private CategoryService service;

    private Category starters;

    private Category cat(long id, String name, boolean active) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setIcon("fa-utensils");
        c.setSortOrder(1);
        c.setActive(active);
        return c;
    }

    private CategoryRequestDTO req(String name, String desc, String icon, Integer order) {
        CategoryRequestDTO r = new CategoryRequestDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setIcon(icon);
        r.setSortOrder(order);
        return r;
    }

    @BeforeEach
    void setUp() {
        starters = cat(1, "Starters", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(starters));
        when(categoryRepository.findAllByNameIgnoreCase(any())).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Editing changes name, description, icon and sort order, and writes a before/after audit entry")
    void editsAllFields() {
        CategoryDTO out = service.updateCategory(1L, req("  Appetizers  ", "Small plates", "fa-burger", 7));
        assertEquals("Appetizers", out.getName());
        assertEquals("Small plates", out.getDescription());
        assertEquals("fa-burger", out.getIcon());
        assertEquals(7, out.getSortOrder());
        verify(auditLogService).log(eq("UPDATE"), eq("CATEGORY"), eq(1L),
                argThat(old -> old.contains("Starters")), argThat(now -> now.contains("Appetizers") && now.contains("fa-burger")));
    }

    @Test
    @DisplayName("Saving a category with its own unchanged name is allowed")
    void ownNameIsNotADuplicate() {
        when(categoryRepository.findAllByNameIgnoreCase("Starters")).thenReturn(List.of(starters));
        assertDoesNotThrow(() -> service.updateCategory(1L, req("starters", "x", null, null)));
    }

    @Test
    @DisplayName("Renaming onto another category's name (any letter case) is refused with a clear message")
    void duplicateNameRefused() {
        when(categoryRepository.findAllByNameIgnoreCase("soups")).thenReturn(List.of(cat(2, "Soups", true)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateCategory(1L, req("soups", null, null, null)));
        assertTrue(e.getMessage().contains("'Soups' already exists"), e.getMessage());
        assertEquals("Starters", starters.getName(), "nothing may change on a refused edit");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Blank, over-long names and malformed icons are refused, for edit and for create")
    void invalidInputRefused() {
        for (String bad : new String[]{null, "", "   ", "x".repeat(101)}) {
            assertThrows(IllegalArgumentException.class, () -> service.updateCategory(1L, req(bad, null, null, null)));
            assertThrows(IllegalArgumentException.class, () -> service.createCategory(req(bad, null, null, null)));
        }
        for (String icon : new String[]{"fa-bug\" onmouseover=\"x", "<img>", "utensils", "fa-", "FA-Burger", "fa-" + "a".repeat(41)}) {
            assertThrows(IllegalArgumentException.class, () -> service.updateCategory(1L, req("Ok", null, icon, null)), icon);
        }
        assertThrows(IllegalArgumentException.class, () -> service.updateCategory(1L, req("Ok", null, null, -1)));
        assertThrows(IllegalArgumentException.class, () -> service.updateCategory(1L, req("Ok", null, null, 10000)));
        assertThrows(IllegalArgumentException.class, () -> service.updateCategory(1L, req("Ok", "d".repeat(256), null, null)));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Omitting icon and sort order on edit keeps the existing values; blank description clears it")
    void omittedFieldsKeepExistingValues() {
        starters.setIcon("fa-pizza-slice");
        starters.setSortOrder(5);
        starters.setDescription("old");
        CategoryDTO out = service.updateCategory(1L, req("Starters", "  ", null, null));
        assertEquals("fa-pizza-slice", out.getIcon());
        assertEquals(5, out.getSortOrder());
        assertNull(out.getDescription());
    }

    @Test
    @DisplayName("Create still defaults the icon and sort order")
    void createDefaults() {
        CategoryDTO out = service.createCategory(req("Desserts", null, null, null));
        assertEquals("fa-utensils", out.getIcon());
        assertEquals(0, out.getSortOrder());
        assertTrue(out.getActive());
    }
}
