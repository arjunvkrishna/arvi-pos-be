package com.neram.pos.service;

import com.neram.pos.dto.ItemsSoldDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.repository.BillItemRepository;
import com.neram.pos.repository.CategoryRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.util.BusinessDate;
import com.neram.pos.util.ItemSearch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/** "What sold": the dishes sold between two business dates, narrowed by search, category and order type, per day or per week. */
@Service
@RequiredArgsConstructor
public class ItemsSoldService {

    public static final int MAX_DAYS = 366;

    private final BillItemRepository billItems;
    private final MenuItemRepository menuItems;
    private final CategoryRepository categories;

    /** One row of the database answer, with the category worked out. */
    record Row(LocalDate day, Long menuItemId, String itemName, String categoryName, Long categoryId, Bill.OrderType orderType,
               long quantity, BigDecimal revenue, long bills) {}

    @Transactional(readOnly = true)
    public ItemsSoldDTO report(LocalDate from, LocalDate to, String groupBy, String query, Set<Long> categoryIds, Set<Bill.OrderType> orderTypes) {
        LocalDate end = to != null ? to : BusinessDate.now();
        LocalDate start = from != null ? from : end.minusDays(6);
        if (start.isAfter(end)) throw new IllegalArgumentException("The start date is after the end date.");
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) throw new IllegalArgumentException("Choose at most " + MAX_DAYS + " days at a time.");

        Map<Long, MenuItem> dishes = menuItems.findAll().stream().collect(Collectors.toMap(MenuItem::getId, m -> m, (a, b) -> a));
        Map<Long, String> categoryNames = categories.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        List<Row> rows = new ArrayList<>();
        for (Object[] r : billItems.findItemSalesRows(start, end)) {
            Long menuItemId = (Long) r[1];
            MenuItem m = menuItemId == null ? null : dishes.get(menuItemId);
            Long categoryId = m != null ? m.getCategoryId() : null;
            rows.add(new Row((LocalDate) r[0], menuItemId, (String) r[2], categoryId == null ? "Other" : categoryNames.getOrDefault(categoryId, "Other"), categoryId,
                    (Bill.OrderType) r[3], ((Number) r[4]).longValue(), r[5] == null ? BigDecimal.ZERO : (BigDecimal) r[5], ((Number) r[6]).longValue()));
        }
        return build(rows, start, end, groupBy, query, categoryIds, orderTypes);
    }

    /** The whole report from the raw rows: filter, add up per dish, and add up per day or per week (days with no sales included). */
    static ItemsSoldDTO build(List<Row> all, LocalDate start, LocalDate end, String groupBy, String query, Set<Long> categoryIds, Set<Bill.OrderType> orderTypes) {
        boolean weekly = "WEEK".equalsIgnoreCase(groupBy);
        List<Row> rows = all.stream()
                .filter(r -> orderTypes == null || orderTypes.isEmpty() || orderTypes.contains(r.orderType()))
                .filter(r -> categoryIds == null || categoryIds.isEmpty() || (r.categoryId() != null && categoryIds.contains(r.categoryId())))
                .filter(r -> ItemSearch.matches(query, r.itemName()))
                .collect(Collectors.toList());

        // per dish over the whole range
        Map<String, Acc> perDish = new LinkedHashMap<>();
        for (Row r : rows) perDish.computeIfAbsent(dishKey(r), k -> new Acc(r.menuItemId(), r.itemName(), r.categoryName())).add(r);
        long totalQty = perDish.values().stream().mapToLong(a -> a.qty).sum();
        BigDecimal totalRevenue = perDish.values().stream().map(a -> a.revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ItemsSoldDTO.DishDTO> items = perDish.values().stream()
                .sorted(Comparator.comparingLong((Acc a) -> a.qty).reversed().thenComparing(a -> a.name, String.CASE_INSENSITIVE_ORDER))
                .map(a -> ItemsSoldDTO.DishDTO.builder().menuItemId(a.menuItemId).itemName(a.name).categoryName(a.category).quantity(a.qty)
                        .revenue(a.revenue.setScale(2, RoundingMode.HALF_UP)).bills(a.bills)
                        .sharePercent(totalQty == 0 ? 0 : Math.round(a.qty * 1000.0 / totalQty) / 10.0).build())
                .collect(Collectors.toList());

        // per day or per week, newest first, empty periods included
        Map<LocalDate, List<Row>> byPeriod = new HashMap<>();
        for (Row r : rows) byPeriod.computeIfAbsent(weekly ? weekStart(r.day()) : r.day(), k -> new ArrayList<>()).add(r);
        List<LocalDate> starts = new ArrayList<>();
        for (LocalDate d = weekly ? weekStart(start) : start; !d.isAfter(end); d = weekly ? d.plusWeeks(1) : d.plusDays(1)) starts.add(d);
        Collections.reverse(starts);
        List<ItemsSoldDTO.PeriodDTO> periods = new ArrayList<>();
        for (LocalDate s : starts) {
            List<Row> in = byPeriod.getOrDefault(s, List.of());
            Map<String, Acc> perItem = new LinkedHashMap<>();
            for (Row r : in) perItem.computeIfAbsent(dishKey(r), k -> new Acc(r.menuItemId(), r.itemName(), r.categoryName())).add(r);
            periods.add(ItemsSoldDTO.PeriodDTO.builder()
                    .start(s.isBefore(start) ? start : s)
                    .end(weekly ? (s.plusDays(6).isAfter(end) ? end : s.plusDays(6)) : s)
                    .quantity(perItem.values().stream().mapToLong(a -> a.qty).sum())
                    .revenue(perItem.values().stream().map(a -> a.revenue).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP))
                    .items(perItem.values().stream()
                            .sorted(Comparator.comparingLong((Acc a) -> a.qty).reversed().thenComparing(a -> a.name, String.CASE_INSENSITIVE_ORDER))
                            .map(a -> ItemsSoldDTO.PeriodDishDTO.builder().itemName(a.name).quantity(a.qty).revenue(a.revenue.setScale(2, RoundingMode.HALF_UP)).build())
                            .collect(Collectors.toList()))
                    .build());
        }
        return ItemsSoldDTO.builder().from(start).to(end).groupBy(weekly ? "WEEK" : "DAY").query(query == null ? "" : query.trim())
                .totalQuantity(totalQty).totalRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP)).dishCount(items.size()).items(items).periods(periods).build();
    }

    /** Weeks run Monday to Sunday. */
    static LocalDate weekStart(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    private static String dishKey(Row r) { return r.menuItemId() != null ? "id:" + r.menuItemId() : "name:" + ItemSearch.normalize(r.itemName()); }

    private static final class Acc {
        final Long menuItemId; final String name; final String category;
        long qty; long bills; BigDecimal revenue = BigDecimal.ZERO;
        Acc(Long menuItemId, String name, String category) { this.menuItemId = menuItemId; this.name = name; this.category = category; }
        void add(Row r) { qty += r.quantity(); bills += r.bills(); revenue = revenue.add(r.revenue()); }
    }
}
