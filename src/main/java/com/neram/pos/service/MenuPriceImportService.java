package com.neram.pos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.neram.pos.dto.MenuPriceImportDTO;
import com.neram.pos.dto.MenuPriceImportDTO.Result;
import com.neram.pos.dto.MenuPriceImportDTO.Row;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.repository.CategoryRepository;
import com.neram.pos.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bulk menu export / price import.
 *
 * Import only ever changes {@code price}, and only for dishes listed in the uploaded file.
 * Dishes are matched by id (authoritative); when a row has no id it falls back to an exact,
 * unambiguous name + category match. Nothing is written by {@link #preview}; {@link #apply}
 * re-validates the same file against the current database before writing.
 */
@Service
@RequiredArgsConstructor
public class MenuPriceImportService {

    static final int MAX_ROWS = 2000;
    static final BigDecimal MAX_PRICE = new BigDecimal("100000");
    static final String FORMAT = "neram-menu";

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    // ------------------------------------------------------------------ export

    public Map<String, Object> export() {
        Map<Long, String> catNames = categoryNames();
        List<Map<String, Object>> items = menuItemRepository.findByActiveTrue().stream()
                .sorted(Comparator.comparing((MenuItem m) -> catNames.getOrDefault(m.getCategoryId(), ""), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(m -> m.getName() == null ? "" : m.getName(), String.CASE_INSENSITIVE_ORDER))
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", m.getId());
                    row.put("name", m.getName());
                    row.put("category", catNames.getOrDefault(m.getCategoryId(), "General"));
                    row.put("price", m.getPrice());
                    row.put("isVeg", m.getIsVeg());
                    row.put("isAvailable", m.getIsAvailable());
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", FORMAT);
        out.put("version", 1);
        out.put("note", "Edit only the \"price\" values. Import updates the price of the dishes listed here and nothing else. "
                + "\"id\" identifies the dish; remove dishes you do not want to touch.");
        out.put("exportedAt", LocalDateTime.now().toString());
        out.put("itemCount", items.size());
        out.put("items", items);
        return out;
    }

    // ----------------------------------------------------------------- preview

    @Transactional(readOnly = true)
    public Result preview(JsonNode root) {
        return evaluate(root).result(false);
    }

    // ------------------------------------------------------------------- apply

    @Transactional
    @CacheEvict(value = "publicMenu", allEntries = true)
    public Result apply(JsonNode root) {
        Evaluation eval = evaluate(root);
        LocalDateTime now = LocalDateTime.now();
        for (Planned p : eval.planned) {
            MenuItem item = p.item;
            String oldVal = item.getName() + " - ₹" + item.getPrice();
            item.setPrice(p.newPrice);
            item.setUpdatedAt(now);
            menuItemRepository.save(item);
            auditLogService.log("PRICE_IMPORT", "MENU_ITEM", item.getId(), oldVal, item.getName() + " - ₹" + p.newPrice);
        }
        return eval.result(true);
    }

    // -------------------------------------------------------------- evaluation

    private static class Planned {
        final MenuItem item;
        final BigDecimal newPrice;
        Planned(MenuItem item, BigDecimal newPrice) { this.item = item; this.newPrice = newPrice; }
    }

    private static class Evaluation {
        final List<Row> rows = new ArrayList<>();
        final List<Planned> planned = new ArrayList<>();

        Result result(boolean applied) {
            int changed = 0, unchanged = 0, notFound = 0, invalid = 0;
            for (Row r : rows) {
                switch (r.getStatus()) {
                    case MenuPriceImportDTO.CHANGED -> changed++;
                    case MenuPriceImportDTO.UNCHANGED -> unchanged++;
                    case MenuPriceImportDTO.NOT_FOUND -> notFound++;
                    default -> invalid++;     // INVALID and DUPLICATE
                }
            }
            return Result.builder().applied(applied).total(rows.size()).changed(changed).unchanged(unchanged)
                    .notFound(notFound).invalid(invalid).rows(rows).build();
        }
    }

    private Evaluation evaluate(JsonNode root) {
        JsonNode items = root == null ? null : (root.isArray() ? root : root.get("items"));
        if (items == null || !items.isArray()) {
            throw new IllegalArgumentException("Invalid file: expected a menu export with an \"items\" list");
        }
        if (items.size() == 0) {
            throw new IllegalArgumentException("The file contains no dishes");
        }
        if (items.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Too many dishes in one file (max " + MAX_ROWS + ")");
        }

        Map<Long, String> catNames = categoryNames();
        List<MenuItem> active = menuItemRepository.findByActiveTrue();
        Map<Long, MenuItem> byId = new HashMap<>();
        Map<String, List<MenuItem>> byNameCat = new HashMap<>();
        for (MenuItem m : active) {
            byId.put(m.getId(), m);
            byNameCat.computeIfAbsent(key(m.getName(), catNames.getOrDefault(m.getCategoryId(), "General")), k -> new ArrayList<>()).add(m);
        }

        Evaluation eval = new Evaluation();
        Set<Long> seen = new HashSet<>();
        int index = 0;
        for (JsonNode node : items) {
            index++;
            Row.RowBuilder row = Row.builder().index(index);
            if (node == null || !node.isObject()) {
                eval.rows.add(row.status(MenuPriceImportDTO.INVALID).message("Entry is not an object").build());
                continue;
            }
            String fileName = text(node.get("name"));
            String fileCat = text(node.get("category"));
            row.name(fileName).categoryName(fileCat);

            // 1) Which dish?
            MenuItem item = null;
            JsonNode idNode = node.get("id");
            if (idNode != null && !idNode.isNull()) {
                if (!idNode.canConvertToLong() || !idNode.isIntegralNumber()) {
                    eval.rows.add(row.status(MenuPriceImportDTO.INVALID).message("\"id\" must be a whole number").build());
                    continue;
                }
                long id = idNode.asLong();
                row.id(id);
                item = byId.get(id);
                if (item == null) {
                    eval.rows.add(row.status(MenuPriceImportDTO.NOT_FOUND).message("No active dish with this id").build());
                    continue;
                }
            } else if (fileName != null && fileCat != null) {
                List<MenuItem> matches = byNameCat.getOrDefault(key(fileName, fileCat), List.of());
                if (matches.isEmpty()) {
                    eval.rows.add(row.status(MenuPriceImportDTO.NOT_FOUND).message("No dish with this name in this category").build());
                    continue;
                }
                if (matches.size() > 1) {
                    eval.rows.add(row.status(MenuPriceImportDTO.INVALID).message("Several dishes share this name and category; use \"id\"").build());
                    continue;
                }
                item = matches.get(0);
                row.id(item.getId());
            } else {
                eval.rows.add(row.status(MenuPriceImportDTO.INVALID).message("Needs an \"id\" (or both \"name\" and \"category\")").build());
                continue;
            }

            String currentCat = catNames.getOrDefault(item.getCategoryId(), "General");
            row.name(item.getName()).categoryName(currentCat).oldPrice(item.getPrice());

            if (!seen.add(item.getId())) {
                eval.rows.add(row.status(MenuPriceImportDTO.DUPLICATE).message("Dish appears more than once in the file; this entry is ignored").build());
                continue;
            }

            // 2) Which price?
            BigDecimal price;
            try {
                price = parsePrice(node.get("price"));
            } catch (IllegalArgumentException e) {
                eval.rows.add(row.status(MenuPriceImportDTO.INVALID).message(e.getMessage()).build());
                continue;
            }
            row.newPrice(price);

            String note = null;
            if (fileName != null && !fileName.trim().equalsIgnoreCase(item.getName().trim())) {
                note = "Name in file (\"" + fileName + "\") differs; the dish name is not changed";
            }

            if (item.getPrice() != null && item.getPrice().compareTo(price) == 0) {
                eval.rows.add(row.status(MenuPriceImportDTO.UNCHANGED).message(note).build());
            } else {
                eval.rows.add(row.status(MenuPriceImportDTO.CHANGED).message(note).build());
                eval.planned.add(new Planned(item, price));
            }
        }
        return eval;
    }

    static BigDecimal parsePrice(JsonNode n) {
        if (n == null || n.isNull()) throw new IllegalArgumentException("Missing \"price\"");
        BigDecimal value;
        if (n.isNumber()) {
            value = n.decimalValue();
        } else if (n.isTextual()) {
            try {
                value = new BigDecimal(n.asText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Price \"" + n.asText() + "\" is not a number");
            }
        } else {
            throw new IllegalArgumentException("Price must be a number");
        }
        if (value.signum() < 0) throw new IllegalArgumentException("Price cannot be negative");
        if (value.compareTo(MAX_PRICE) > 0) throw new IllegalArgumentException("Price is above the allowed maximum (" + MAX_PRICE.toPlainString() + ")");
        if (value.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Price can have at most 2 decimal places");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private Map<Long, String> categoryNames() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull() || !n.isTextual()) return null;
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String key(String name, String category) {
        return (name == null ? "" : name.trim().toLowerCase()) + "|" + (category == null ? "" : category.trim().toLowerCase());
    }
}
