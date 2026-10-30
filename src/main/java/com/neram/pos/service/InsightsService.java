package com.neram.pos.service;

import com.neram.pos.dto.InsightsDTO;
import com.neram.pos.dto.InsightsDTO.*;
import com.neram.pos.dto.InventoryItemDTO;
import com.neram.pos.dto.RecipeDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillRefund;
import com.neram.pos.entity.Category;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.User;
import com.neram.pos.repository.BillRefundRepository;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.CategoryRepository;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.InventoryTransactionRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Gathers what the analytics "insights" need (bills, recipes, stock, refunds, waste) and hands it to {@link InsightsMath}. */
@Service
@RequiredArgsConstructor
public class InsightsService {
    private final BillRepository billRepository;
    private final BillRefundRepository billRefundRepository;
    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final RecipeService recipeService;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryService inventoryService;
    private final com.neram.pos.repository.BillPaymentRepository billPaymentRepository;

    private static final Set<String> WASTE_REASONS = Set.of("EXPIRED", "SPOILED", "BREAKAGE", "STAFF_MEAL");

    @Transactional(readOnly = true)
    /** Parts of the bills that were paid in more than one way, by bill id. */
    private Map<Long, List<SplitPayments.Part>> splitPartsOf(List<Bill> bills) {
        List<Long> ids = bills.stream().filter(b -> b.getPaymentSummary() != null).map(Bill::getId).collect(Collectors.toList());
        if (ids.isEmpty()) return Map.of();
        Map<Long, List<SplitPayments.Part>> out = new HashMap<>();
        for (com.neram.pos.entity.BillPayment p : billPaymentRepository.findByBillIdIn(ids))
            out.computeIfAbsent(p.getBillId(), k -> new ArrayList<>()).add(new SplitPayments.Part(Bill.PaymentMode.valueOf(p.getMode()), p.getAmount(), p.getReference()));
        return out;
    }

    public InsightsDTO getInsights(String periodParam, LocalDate customStart, LocalDate customEnd) {
        AnalyticsPeriod p = AnalyticsPeriod.resolve(periodParam, customStart, customEnd, LocalDate.now());
        LocalDateTime from = p.start().atStartOfDay(); LocalDateTime to = p.end().atTime(LocalTime.MAX);
        LocalDateTime pFrom = p.prevStart().atStartOfDay(); LocalDateTime pTo = p.prevEnd().atTime(LocalTime.MAX);

        List<Bill> bills = billRepository.findByCreatedAtBetweenAndStatusOrderByCreatedAtDesc(from, to, Bill.Status.PAID);
        List<Bill> prev = billRepository.findByCreatedAtBetweenAndStatusOrderByCreatedAtDesc(pFrom, pTo, Bill.Status.PAID);

        Map<Long, String> categoryName = categoryRepository.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        List<MenuItem> menu = menuItemRepository.findAll();
        Map<Long, String> categoryOfDish = new HashMap<>();
        for (MenuItem m : menu) categoryOfDish.put(m.getId(), categoryName.getOrDefault(m.getCategoryId(), "Other"));

        Map<Long, Double> costPerServing = new HashMap<>();
        for (RecipeDTO.RecipeResponse r : recipeService.getAllRecipes()) {
            if (Boolean.FALSE.equals(r.getActive()) || r.getCostPerServing() == null) continue;
            costPerServing.merge(r.getMenuItemId(), r.getCostPerServing().doubleValue(), Double::sum);
        }

        Set<Long> userIds = bills.stream().map(Bill::getCreatedBy).filter(x -> x != null).collect(Collectors.toSet());
        Map<Long, String> names = userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        // refunds, voids, cancellations
        List<BillRefund> refunds = billRefundRepository.findByRefundedAtBetween(from, to).stream().filter(r -> r.getStatus() == BillRefund.RefundStatus.SUCCESS).collect(Collectors.toList());
        List<BillRefund> prevRefunds = billRefundRepository.findByRefundedAtBetween(pFrom, pTo).stream().filter(r -> r.getStatus() == BillRefund.RefundStatus.SUCCESS).collect(Collectors.toList());
        double refundAmount = InsightsMath.r2(refunds.stream().mapToDouble(r -> InsightsMath.d(r.getAmount())).sum());
        double prevRefundAmount = InsightsMath.r2(prevRefunds.stream().mapToDouble(r -> InsightsMath.d(r.getAmount())).sum());
        List<Bill> lost = billRepository.findByCreatedAtBetweenAndStatusIn(from, to, List.of(Bill.Status.VOIDED, Bill.Status.CANCELLED));
        List<Bill> prevLost = billRepository.findByCreatedAtBetweenAndStatusIn(pFrom, pTo, List.of(Bill.Status.VOIDED, Bill.Status.CANCELLED));
        List<Bill> voided = lost.stream().filter(b -> b.getStatus() == Bill.Status.VOIDED).collect(Collectors.toList());
        long prevVoided = prevLost.stream().filter(b -> b.getStatus() == Bill.Status.VOIDED).count();

        Stock stock = stock(from, to);
        Losses losses = Losses.builder().refundAmount(refundAmount).refundCount(refunds.size()).voidedCount(voided.size())
                .voidedValue(InsightsMath.r2(voided.stream().mapToDouble(b -> InsightsMath.d(b.getSubtotal())).sum()))
                .cancelledCount((int) lost.stream().filter(b -> b.getStatus() == Bill.Status.CANCELLED).count()).wasteCost(stock.getWasteCost()).build();

        double sales = InsightsMath.sales(bills), prevSales = InsightsMath.sales(prev);
        int orders = bills.size(), prevOrders = prev.size();
        int items = InsightsMath.itemCount(bills), prevItems = InsightsMath.itemCount(prev);
        Compare compare = Compare.builder()
                .sales(InsightsMath.metric(sales, prevSales)).orders(InsightsMath.metric(orders, prevOrders))
                .avgOrder(InsightsMath.metric(orders > 0 ? sales / orders : 0, prevOrders > 0 ? prevSales / prevOrders : 0))
                .itemsPerOrder(InsightsMath.metric(orders > 0 ? (double) items / orders : 0, prevOrders > 0 ? (double) prevItems / prevOrders : 0))
                .discounts(InsightsMath.metric(InsightsMath.discountTotal(bills), InsightsMath.discountTotal(prev)))
                .refunds(InsightsMath.metric(refundAmount, prevRefundAmount)).voids(InsightsMath.metric(voided.size(), prevVoided)).build();

        List<DishRow> dishes = InsightsMath.dishes(bills, costPerServing, categoryOfDish, 40);
        Set<Long> sold = bills.stream().filter(b -> b.getItems() != null).flatMap(b -> b.getItems().stream()).map(i -> i.getMenuItemId()).filter(x -> x != null).collect(Collectors.toSet());
        List<NameRow> never = InsightsMath.neverSold(menu.stream().filter(m -> !Boolean.FALSE.equals(m.getActive())).collect(Collectors.toList()), sold, categoryOfDish);

        return InsightsDTO.builder().start(p.start()).end(p.end()).prevStart(p.prevStart()).prevEnd(p.prevEnd()).compare(compare)
                .byHour(InsightsMath.byHour(bills)).byWeekday(InsightsMath.byWeekday(bills, p.start(), p.end())).heatmap(InsightsMath.heatmap(bills))
                .byOrderType(InsightsMath.byOrderType(bills)).byPaymentMode(InsightsMath.byPaymentMode(bills, splitPartsOf(bills))).byCategory(InsightsMath.byCategory(bills, categoryOfDish))
                .byStaff(InsightsMath.byStaff(bills, names)).byTable(InsightsMath.byTable(bills, 8)).dineIn(InsightsMath.dineIn(bills)).discounts(InsightsMath.discounts(bills)).losses(losses).tax(InsightsMath.tax(bills))
                .dishes(dishes).neverSold(never.size() > 40 ? new ArrayList<>(never.subList(0, 40)) : never).neverSoldCount(never.size()).stock(stock).build();
    }

    /** Stock health: value by category, what is below zero / low / expiring, and the cost of waste in the period by reason. */
    private Stock stock(LocalDateTime from, LocalDateTime to) {
        List<InventoryItem> items = inventoryItemRepository.findByActiveTrue();
        Map<Long, InventoryItem> byId = items.stream().collect(Collectors.toMap(InventoryItem::getId, i -> i, (a, b) -> a));
        Map<String, double[]> value = new LinkedHashMap<>();
        for (InventoryItem i : items) {
            double qty = i.getQuantity() != null ? i.getQuantity() : 0.0;
            double v = Math.max(0.0, qty) * InsightsMath.d(i.getCostPerUnit());
            double[] a = value.computeIfAbsent(i.getItemCategory() != null ? i.getItemCategory() : "Stock", k -> new double[3]);
            a[1] += v; a[0]++;
        }
        double totalValue = value.values().stream().mapToDouble(v -> v[1]).sum();
        List<Slice> valueSlices = value.entrySet().stream().map(e -> Slice.builder().label(e.getKey()).orders((int) e.getValue()[0]).amount(InsightsMath.r2(e.getValue()[1]))
                .share(totalValue > 0 ? InsightsMath.r1(e.getValue()[1] / totalValue * 100.0) : 0).build()).sorted(Comparator.comparingDouble(Slice::getAmount).reversed()).collect(Collectors.toList());

        List<InventoryItemDTO> dtos = inventoryService.getAllItems();
        int negative = (int) items.stream().filter(i -> i.getQuantity() != null && i.getQuantity() < 0).count();
        int low = (int) items.stream().filter(i -> i.getQuantity() != null && i.getMinQuantity() != null && i.getQuantity() <= i.getMinQuantity()).count();
        int expiring = (int) dtos.stream().filter(i -> "SOON".equals(i.getExpiryStatus())).count();
        int expired = (int) dtos.stream().filter(i -> "EXPIRED".equals(i.getExpiryStatus())).count();

        Map<String, double[]> waste = new LinkedHashMap<>();
        for (InventoryTransaction t : inventoryTransactionRepository.findByCreatedAtBetween(from, to)) {
            String reason = null;
            if ("WASTE".equals(t.getTransactionType())) reason = "Food made and discarded";
            else if ("STOCK_OUT".equals(t.getTransactionType()) && t.getReasonType() != null && WASTE_REASONS.contains(t.getReasonType())) reason = label(t.getReasonType());
            if (reason == null || t.getQuantity() == null) continue;
            InventoryItem inv = byId.get(t.getItemId());
            double unit = t.getUnitPrice() != null ? t.getUnitPrice().doubleValue() : inv != null ? InsightsMath.d(inv.getCostPerUnit()) : 0.0;
            double[] a = waste.computeIfAbsent(reason, k -> new double[3]);
            a[0]++; a[1] += t.getQuantity() * unit;
        }
        double wasteCost = waste.values().stream().mapToDouble(v -> v[1]).sum();
        List<Slice> wasteSlices = waste.entrySet().stream().map(e -> Slice.builder().label(e.getKey()).orders((int) e.getValue()[0]).amount(InsightsMath.r2(e.getValue()[1]))
                .share(wasteCost > 0 ? InsightsMath.r1(e.getValue()[1] / wasteCost * 100.0) : 0).build()).sorted(Comparator.comparingDouble(Slice::getAmount).reversed()).collect(Collectors.toList());
        return Stock.builder().valueByCategory(valueSlices).totalValue(InsightsMath.r2(totalValue)).negativeCount(negative).lowCount(low).expiringCount(expiring).expiredCount(expired)
                .wasteByReason(wasteSlices).wasteCost(InsightsMath.r2(wasteCost)).build();
    }

    private static String label(String reason) {
        switch (reason) { case "EXPIRED": return "Expired"; case "SPOILED": return "Spoiled"; case "BREAKAGE": return "Broken / damaged"; case "STAFF_MEAL": return "Staff meals"; default: return reason; }
    }
}
