package com.neram.pos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neram.pos.dto.MenuPriceImportDTO;
import com.neram.pos.dto.MenuPriceImportDTO.Result;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.repository.CategoryRepository;
import com.neram.pos.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuPriceImportServiceTest {

    @Mock private MenuItemRepository menuItemRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private MenuPriceImportService service;

    private final ObjectMapper mapper = new ObjectMapper();
    private MenuItem dosa;
    private MenuItem juice;

    private MenuItem item(long id, String name, long catId, String price) {
        MenuItem m = new MenuItem();
        m.setId(id);
        m.setName(name);
        m.setCategoryId(catId);
        m.setPrice(new BigDecimal(price));
        m.setIsVeg(true);
        m.setIsAvailable(true);
        m.setActive(true);
        return m;
    }

    private Category cat(long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @BeforeEach
    void setUp() {
        dosa = item(1, "Masala Dosa", 10, "80.00");
        juice = item(2, "Orange Juice", 20, "60.00");
        when(menuItemRepository.findByActiveTrue()).thenReturn(List.of(dosa, juice));
        when(categoryRepository.findAll()).thenReturn(List.of(cat(10, "Tiffin"), cat(20, "Juices")));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Export lists every active dish with id, category and price")
    @SuppressWarnings("unchecked")
    void exportListsActiveDishes() {
        Map<String, Object> out = service.export();
        assertEquals("neram-menu", out.get("format"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");
        assertEquals(2, items.size());
        assertEquals("Juices", items.get(0).get("category"));      // sorted by category then name
        assertEquals(new BigDecimal("80.00"), items.get(1).get("price"));
    }

    @Test
    @DisplayName("Preview reports changes but writes nothing")
    void previewWritesNothing() throws Exception {
        Result r = service.preview(json("{\"items\":[{\"id\":1,\"price\":90},{\"id\":2,\"price\":60}]}"));
        assertFalse(r.isApplied());
        assertEquals(1, r.getChanged());
        assertEquals(1, r.getUnchanged());
        assertEquals(new BigDecimal("80.00"), r.getRows().get(0).getOldPrice());
        assertEquals(new BigDecimal("90.00"), r.getRows().get(0).getNewPrice());
        verify(menuItemRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), anyString(), any(), any(), any());
        assertEquals(new BigDecimal("80.00"), dosa.getPrice());
    }

    @Test
    @DisplayName("Apply changes only the price of dishes in the file")
    void applyChangesOnlyListedPrices() throws Exception {
        Result r = service.apply(json("{\"items\":[{\"id\":1,\"name\":\"Renamed In File\",\"price\":\"95.5\"}]}"));
        assertTrue(r.isApplied());
        assertEquals(1, r.getChanged());
        assertEquals(new BigDecimal("95.50"), dosa.getPrice());
        assertEquals("Masala Dosa", dosa.getName());                       // name never changes
        assertEquals(new BigDecimal("60.00"), juice.getPrice());           // not in file -> untouched
        verify(menuItemRepository, times(1)).save(any(MenuItem.class));
        verify(auditLogService).log(eq("PRICE_IMPORT"), eq("MENU_ITEM"), eq(1L), anyString(), anyString());
        assertNotNull(r.getRows().get(0).getMessage());                     // warns about the differing name
    }

    @Test
    @DisplayName("Rows without id match on exact name + category")
    void matchesByNameAndCategory() throws Exception {
        Result r = service.apply(json("[{\"name\":\"orange juice\",\"category\":\"JUICES\",\"price\":65}]"));
        assertEquals(1, r.getChanged());
        assertEquals(new BigDecimal("65.00"), juice.getPrice());
    }

    @Test
    @DisplayName("Unknown, invalid and duplicate rows are skipped, valid rows still apply")
    void badRowsAreSkipped() throws Exception {
        Result r = service.apply(json("{\"items\":["
                + "{\"id\":999,\"price\":10},"          // not found
                + "{\"id\":1,\"price\":-5},"            // negative -> invalid
                + "{\"id\":1,\"price\":\"abc\"},"       // same dish again -> duplicate, ignored
                + "{\"id\":2,\"price\":12.345},"        // 3 decimals -> invalid
                + "{\"id\":2,\"price\":70}"             // same dish again -> duplicate, ignored
                + "]}"));
        assertEquals(1, r.getNotFound());
        assertEquals(4, r.getInvalid());          // 2 invalid + 2 duplicates
        assertEquals(0, r.getChanged());
        assertEquals(new BigDecimal("80.00"), dosa.getPrice());
        assertEquals(new BigDecimal("60.00"), juice.getPrice());
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Prices above the cap, non-numeric text and missing price are invalid")
    void priceValidation() throws Exception {
        Result r = service.preview(json("{\"items\":[{\"id\":1,\"price\":100000.01},{\"id\":2}]}"));
        assertEquals(2, r.getInvalid());
        assertEquals(0, r.getChanged());
    }

    @Test
    @DisplayName("A file that is not a menu export is rejected")
    void rejectsGarbage() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.preview(json("{\"foo\":1}")));
        assertThrows(IllegalArgumentException.class, () -> service.preview(json("{\"items\":[]}")));
    }
}
