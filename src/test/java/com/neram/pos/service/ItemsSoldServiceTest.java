package com.neram.pos.service;

import com.neram.pos.dto.ItemsSoldDTO;
import com.neram.pos.entity.Bill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The items-sold report: filtering, adding up per dish, and per day or week (days with no sales included). */
class ItemsSoldServiceTest {

    // 2027-03-01 is a Monday
    private static final LocalDate MON = LocalDate.of(2027, 3, 1);

    private static ItemsSoldService.Row row(LocalDate day, long id, String name, String category, long catId, Bill.OrderType type, long qty, String revenue, long bills) {
        return new ItemsSoldService.Row(day, id, name, category, catId, type, qty, new BigDecimal(revenue), bills);
    }

    private static List<ItemsSoldService.Row> sample() {
        return List.of(
                row(MON, 1, "Chicken Biryani", "Mains", 10, Bill.OrderType.TABLE, 5, "1000.00", 4),
                row(MON, 1, "Chicken Biryani", "Mains", 10, Bill.OrderType.TAKEAWAY, 2, "400.00", 2),
                row(MON.plusDays(2), 1, "Chicken Biryani", "Mains", 10, Bill.OrderType.TABLE, 3, "600.00", 3),
                row(MON.plusDays(2), 2, "Mutton Biriyani", "Mains", 10, Bill.OrderType.TABLE, 1, "300.00", 1),
                row(MON.plusDays(2), 3, "Masala Tea", "Drinks", 20, Bill.OrderType.OUTDOOR, 8, "160.00", 6),
                row(MON.plusDays(8), 3, "Masala Tea", "Drinks", 20, Bill.OrderType.TABLE, 4, "80.00", 4));
    }

    private static ItemsSoldDTO day(String q, Set<Long> cats, Set<Bill.OrderType> types) {
        return ItemsSoldService.build(sample(), MON, MON.plusDays(9), "DAY", q, cats, types);
    }

    @Test
    @DisplayName("everything, per dish: quantities and revenue add up, most sold first, with shares")
    void perDish() {
        ItemsSoldDTO r = day("", Set.of(), Set.of());
        assertEquals(23, r.getTotalQuantity());
        assertEquals(0, new BigDecimal("2540.00").compareTo(r.getTotalRevenue()));
        assertEquals(3, r.getDishCount());
        assertEquals("Masala Tea", r.getItems().get(0).getItemName());
        assertEquals(12, r.getItems().get(0).getQuantity());
        assertEquals("Chicken Biryani", r.getItems().get(1).getItemName());
        assertEquals(10, r.getItems().get(1).getQuantity());
        assertEquals(9, r.getItems().get(1).getBills(), "4 + 2 + 3 bills across the rows");
        assertEquals(52.2, r.getItems().get(0).getSharePercent(), 0.05);
    }

    @Test
    @DisplayName("search \"biriyani\" finds both spellings and says how many sold")
    void searchBiriyani() {
        ItemsSoldDTO r = day("biriyani", Set.of(), Set.of());
        assertEquals(11, r.getTotalQuantity());
        assertEquals(2, r.getDishCount());
        assertEquals(List.of("Chicken Biryani", "Mutton Biriyani"), r.getItems().stream().map(ItemsSoldDTO.DishDTO::getItemName).toList());
        assertEquals("biriyani", r.getQuery());
    }

    @Test
    @DisplayName("category and order-type filters narrow the answer, and they combine")
    void filtersCombine() {
        assertEquals(11, day("", Set.of(10L), Set.of()).getTotalQuantity());
        assertEquals(12, day("", Set.of(20L), Set.of()).getTotalQuantity());
        assertEquals(2, day("", Set.of(), Set.of(Bill.OrderType.TAKEAWAY)).getTotalQuantity());
        assertEquals(8, day("", Set.of(20L), Set.of(Bill.OrderType.OUTDOOR)).getTotalQuantity());
        assertEquals(0, day("biriyani", Set.of(20L), Set.of()).getTotalQuantity());
    }

    @Test
    @DisplayName("day by day: every day of the range, newest first, days with no sales shown as zero")
    void dayWise() {
        ItemsSoldDTO r = day("", Set.of(), Set.of());
        assertEquals(10, r.getPeriods().size());
        assertEquals(MON.plusDays(9), r.getPeriods().get(0).getStart());
        assertEquals(0, r.getPeriods().get(0).getQuantity());
        ItemsSoldDTO.PeriodDTO wed = r.getPeriods().stream().filter(p -> p.getStart().equals(MON.plusDays(2))).findFirst().orElseThrow();
        assertEquals(12, wed.getQuantity());
        assertEquals(3, wed.getItems().size());
        assertEquals("Masala Tea", wed.getItems().get(0).getItemName());
        assertEquals(MON.plusDays(2), wed.getEnd());
    }

    @Test
    @DisplayName("week by week: Monday to Sunday, cut to the chosen range, with each week's dishes")
    void weekWise() {
        // the database only returns rows inside the chosen range
        List<ItemsSoldService.Row> inRange = sample().stream().filter(x -> !x.day().isBefore(MON.plusDays(2))).toList();
        ItemsSoldDTO r = ItemsSoldService.build(inRange, MON.plusDays(2), MON.plusDays(9), "WEEK", "", Set.of(), Set.of());
        assertEquals("WEEK", r.getGroupBy());
        assertEquals(2, r.getPeriods().size());
        ItemsSoldDTO.PeriodDTO latest = r.getPeriods().get(0);
        assertEquals(MON.plusDays(7), latest.getStart());
        assertEquals(MON.plusDays(9), latest.getEnd(), "the second week is cut at the end of the range");
        assertEquals(4, latest.getQuantity());
        ItemsSoldDTO.PeriodDTO first = r.getPeriods().get(1);
        assertEquals(MON.plusDays(2), first.getStart(), "the first week starts at the chosen first day, not the Monday before");
        assertEquals(MON.plusDays(6), first.getEnd());
        assertEquals(12, first.getQuantity());
    }

    @Test
    @DisplayName("search also narrows each day: \"biriyani\" per day shows only biryani")
    void searchPerDay() {
        ItemsSoldDTO r = day("biriyani", Set.of(), Set.of());
        long onWednesday = r.getPeriods().stream().filter(p -> p.getStart().equals(MON.plusDays(2))).findFirst().orElseThrow().getQuantity();
        assertEquals(4, onWednesday);
        assertTrue(r.getPeriods().stream().flatMap(p -> p.getItems().stream()).noneMatch(i -> i.getItemName().contains("Tea")));
    }

    @Test
    @DisplayName("nothing sold: a clean empty report with zero totals")
    void nothingSold() {
        ItemsSoldDTO r = ItemsSoldService.build(List.of(), MON, MON.plusDays(2), "DAY", "", Set.of(), Set.of());
        assertEquals(0, r.getTotalQuantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getTotalRevenue()));
        assertEquals(0, r.getDishCount());
        assertEquals(3, r.getPeriods().size());
    }

    @Test
    @DisplayName("a dish with no menu id is still counted, grouped by its name")
    void dishWithoutMenuId() {
        List<ItemsSoldService.Row> rows = List.of(
                new ItemsSoldService.Row(MON, null, "Special Thali", "Other", null, Bill.OrderType.TABLE, 2, new BigDecimal("300"), 2),
                new ItemsSoldService.Row(MON.plusDays(1), null, "special thali", "Other", null, Bill.OrderType.TABLE, 1, new BigDecimal("150"), 1));
        ItemsSoldDTO r = ItemsSoldService.build(rows, MON, MON.plusDays(1), "DAY", "", Set.of(), Set.of());
        assertEquals(1, r.getDishCount());
        assertEquals(3, r.getTotalQuantity());
    }
}
