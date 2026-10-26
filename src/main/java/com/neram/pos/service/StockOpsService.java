package com.neram.pos.service;

import com.neram.pos.dto.RecipeDTO;
import com.neram.pos.dto.StockOpsDTO;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.Supplier;
import com.neram.pos.entity.SupplierInvoice;
import com.neram.pos.entity.SupplierInvoiceItem;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.InventoryTransactionRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.repository.SupplierInvoiceItemRepository;
import com.neram.pos.repository.SupplierInvoiceRepository;
import com.neram.pos.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** What to buy next, and which prices moved: reorder suggestions, ingredient cost changes and dishes whose margin is thin. */
@Service
@RequiredArgsConstructor
public class StockOpsService {

    public static final String KEY_CHANGE = "COST_ALERT_CHANGE_PERCENT";
    public static final String KEY_MARGIN = "COST_ALERT_MARGIN_FLOOR_PERCENT";
    public static final double DEFAULT_CHANGE = 10.0;
    public static final double DEFAULT_MARGIN = 30.0;

    private final InventoryItemRepository items;
    private final InventoryTransactionRepository transactions;
    private final SupplierInvoiceItemRepository invoiceItems;
    private final SupplierInvoiceRepository invoices;
    private final SupplierRepository suppliers;
    private final MenuItemRepository menu;
    private final RecipeService recipeService;
    private final AppSettingService settings;

    /** Net units used per item over the last USAGE_DAYS days: sales, issues and waste, minus refunds put back. */
    private Map<Long, Double> usage(LocalDateTime now) {
        Map<Long, Double> out = new HashMap<>();
        for (InventoryTransaction t : transactions.findByCreatedAtBetween(now.minusDays(StockOpsMath.USAGE_DAYS), now)) {
            String type = t.getTransactionType();
            double q = t.getQuantity() != null ? t.getQuantity() : 0.0;
            if ("STOCK_OUT".equals(type) || "WASTE".equals(type) || "SALE_CONSUMPTION".equals(type)) out.merge(t.getItemId(), q, Double::sum);
            else if ("REFUND_REVERSAL".equals(type)) out.merge(t.getItemId(), -q, Double::sum);
        }
        return out;
    }

    /** Purchase lines of the given items, newest first, each with its per-stock-unit cost and supplier. */
    private record Buy(Long itemId, double unitCost, LocalDate date, long invoiceId, Long supplierId) {}

    private Map<Long, List<Buy>> purchases(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) return Map.of();
        List<SupplierInvoiceItem> lines = invoiceItems.findByInventoryItemIdIn(itemIds);
        Map<Long, SupplierInvoice> inv = invoices.findAllById(lines.stream().map(SupplierInvoiceItem::getInvoiceId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SupplierInvoice::getId, x -> x));
        Map<Long, List<Buy>> out = new HashMap<>();
        for (SupplierInvoiceItem l : lines) {
            SupplierInvoice i = inv.get(l.getInvoiceId());
            if (i == null || l.getInventoryItemId() == null) continue;
            double cost = StockOpsMath.unitCost(l.getTotalPrice() != null ? l.getTotalPrice().doubleValue() : 0, l.getStockQuantity(), l.getUnitPrice() != null ? l.getUnitPrice().doubleValue() : 0);
            if (cost <= 0) continue;
            out.computeIfAbsent(l.getInventoryItemId(), k -> new ArrayList<>()).add(new Buy(l.getInventoryItemId(), cost, i.getInvoiceDate(), i.getId(), i.getSupplierId()));
        }
        out.values().forEach(v -> v.sort(Comparator.comparing(Buy::date, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingLong(Buy::invoiceId).reversed()));
        return out;
    }

    @Transactional(readOnly = true)
    public StockOpsDTO.Reorder reorder() {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Double> used = usage(now);
        List<InventoryItem> all = items.findByActiveTrue().stream()
                .filter(i -> !"Fixed asset".equalsIgnoreCase(i.getItemCategory())).toList();
        Map<Long, List<Buy>> buys = purchases(all.stream().map(InventoryItem::getId).toList());
        Map<Long, String> supplierName = suppliers.findAll().stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));

        List<StockOpsDTO.ReorderRow> rows = new ArrayList<>();
        for (InventoryItem i : all) {
            double qty = i.getQuantity() != null ? i.getQuantity() : 0.0;
            double min = i.getMinQuantity() != null ? i.getMinQuantity() : 0.0;
            Double avg = used.containsKey(i.getId()) ? StockOpsMath.round(Math.max(0, used.get(i.getId())) / StockOpsMath.USAGE_DAYS, 3) : null;
            String reason = StockOpsMath.reorderReason(qty, min, avg, StockOpsMath.COVER_DAYS);
            if (reason == null) continue;
            double units = StockOpsMath.suggestedUnits(qty, min, avg, i.getPackSize(), StockOpsMath.COVER_DAYS);
            if (units <= 0) units = StockOpsMath.suggestedUnits(qty, Math.max(min, 1), avg, i.getPackSize(), StockOpsMath.COVER_DAYS);
            List<Buy> b = buys.getOrDefault(i.getId(), List.of());
            Long sid = b.isEmpty() ? null : b.get(0).supplierId();
            String sname = sid != null ? supplierName.get(sid) : null;
            if (sname == null) sname = i.getSupplier();
            double cost = !b.isEmpty() ? b.get(0).unitCost() : (i.getCostPerUnit() != null ? i.getCostPerUnit().doubleValue() : 0);
            boolean packed = i.getPackSize() != null && i.getPackSize() > 1;
            rows.add(StockOpsDTO.ReorderRow.builder().itemId(i.getId()).name(i.getName()).unit(i.getUnit()).category(i.getItemCategory())
                    .quantity(qty).minQuantity(min).reason(reason).avgDailyUse(avg).daysLeft(StockOpsMath.daysLeft(qty, avg))
                    .suggestedUnits(units).suggestedPacks(packed ? StockOpsMath.round(units / i.getPackSize(), 3) : null)
                    .packName(packed ? i.getPackName() : null).packSize(packed ? i.getPackSize() : null)
                    .supplierId(sid).supplier(sname).lastUnitCost(StockOpsMath.round(cost, 2)).estimatedCost(StockOpsMath.round(units * cost, 2)).build());
        }
        Map<String, Integer> rank = Map.of("OUT", 0, "BELOW_MIN", 1, "RUNS_OUT_SOON", 2);
        rows.sort(Comparator.comparing((StockOpsDTO.ReorderRow r) -> r.getSupplier() == null ? "~" : r.getSupplier().toLowerCase())
                .thenComparing(r -> rank.get(r.getReason())).thenComparing(r -> r.getName().toLowerCase()));
        double total = StockOpsMath.round(rows.stream().mapToDouble(StockOpsDTO.ReorderRow::getEstimatedCost).sum(), 2);
        return StockOpsDTO.Reorder.builder().usageDays(StockOpsMath.USAGE_DAYS).coverDays(StockOpsMath.COVER_DAYS).items(rows).estimatedTotal(total).build();
    }

    public StockOpsDTO.CostAlertSettings getSettings() {
        return new StockOpsDTO.CostAlertSettings(read(KEY_CHANGE, DEFAULT_CHANGE), read(KEY_MARGIN, DEFAULT_MARGIN));
    }

    public StockOpsDTO.CostAlertSettings saveSettings(StockOpsDTO.CostAlertSettings s, String username) {
        if (s.getChangeThresholdPercent() == null || s.getChangeThresholdPercent() < 1 || s.getChangeThresholdPercent() > 200) throw new IllegalArgumentException("Price change alert must be between 1% and 200%.");
        if (s.getMarginFloorPercent() == null || s.getMarginFloorPercent() < 0 || s.getMarginFloorPercent() > 95) throw new IllegalArgumentException("Minimum margin must be between 0% and 95%.");
        settings.setSetting(KEY_CHANGE, String.valueOf(s.getChangeThresholdPercent()), username);
        settings.setSetting(KEY_MARGIN, String.valueOf(s.getMarginFloorPercent()), username);
        return getSettings();
    }

    private double read(String key, double def) {
        try { return Double.parseDouble(settings.getSetting(key, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    @Transactional(readOnly = true)
    public StockOpsDTO.CostAlerts costAlerts() {
        StockOpsDTO.CostAlertSettings cfg = getSettings();
        double threshold = cfg.getChangeThresholdPercent(); double floor = cfg.getMarginFloorPercent();
        List<InventoryItem> all = items.findByActiveTrue();
        Map<Long, InventoryItem> byId = all.stream().collect(Collectors.toMap(InventoryItem::getId, x -> x));
        Map<Long, List<Buy>> buys = purchases(byId.keySet());
        Map<Long, String> supplierName = suppliers.findAll().stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));

        List<RecipeDTO.RecipeResponse> recipes = recipeService.getAllRecipes().stream().filter(r -> !Boolean.FALSE.equals(r.getActive())).toList();
        Map<Long, List<String>> dishesOfItem = new HashMap<>();
        for (RecipeDTO.RecipeResponse r : recipes) dishesOfItem.computeIfAbsent(r.getInventoryItemId(), k -> new ArrayList<>()).add(r.getMenuItemName());

        List<StockOpsDTO.CostChange> changes = new ArrayList<>();
        Set<Long> risen = new HashSet<>();
        for (Map.Entry<Long, List<Buy>> e : buys.entrySet()) {
            List<Buy> b = e.getValue();
            if (b.size() < 2) continue;
            Double pct = StockOpsMath.changePercent(b.get(1).unitCost(), b.get(0).unitCost());
            if (pct == null || Math.abs(pct) < threshold) continue;
            InventoryItem i = byId.get(e.getKey());
            if (i == null) continue;
            if (pct > 0) risen.add(i.getId());
            changes.add(StockOpsDTO.CostChange.builder().itemId(i.getId()).name(i.getName()).unit(i.getUnit())
                    .supplier(b.get(0).supplierId() != null ? supplierName.get(b.get(0).supplierId()) : i.getSupplier())
                    .previousCost(StockOpsMath.round(b.get(1).unitCost(), 2)).latestCost(StockOpsMath.round(b.get(0).unitCost(), 2)).changePercent(pct)
                    .direction(pct > 0 ? "UP" : "DOWN").previousDate(b.get(1).date()).latestDate(b.get(0).date())
                    .dishes(dishesOfItem.getOrDefault(i.getId(), List.of()).stream().distinct().sorted().toList()).build());
        }
        changes.sort(Comparator.comparing((StockOpsDTO.CostChange c) -> -Math.abs(c.getChangePercent())));

        Map<Long, Double> cost = new HashMap<>();
        Map<Long, List<Long>> ingredients = new HashMap<>();
        for (RecipeDTO.RecipeResponse r : recipes) {
            if (r.getCostPerServing() != null) cost.merge(r.getMenuItemId(), r.getCostPerServing().doubleValue(), Double::sum);
            ingredients.computeIfAbsent(r.getMenuItemId(), k -> new ArrayList<>()).add(r.getInventoryItemId());
        }
        List<StockOpsDTO.DishMargin> low = new ArrayList<>();
        for (MenuItem m : menu.findAll()) {
            if (!Boolean.TRUE.equals(m.getActive()) || !cost.containsKey(m.getId()) || m.getPrice() == null) continue;
            double price = m.getPrice().doubleValue(); double c = cost.get(m.getId());
            Double margin = StockOpsMath.marginPercent(price, c);
            if (margin == null || margin >= floor) continue;
            List<String> affected = ingredients.getOrDefault(m.getId(), List.of()).stream().filter(risen::contains).map(id -> byId.get(id).getName()).distinct().sorted().toList();
            low.add(StockOpsDTO.DishMargin.builder().menuItemId(m.getId()).name(m.getName()).price(StockOpsMath.round(price, 2)).cost(StockOpsMath.round(c, 2))
                    .profit(StockOpsMath.round(price - c, 2)).marginPercent(margin).status(margin < 0 ? "NEGATIVE" : "LOW").affectedBy(affected).build());
        }
        low.sort(Comparator.comparing(StockOpsDTO.DishMargin::getMarginPercent));
        return StockOpsDTO.CostAlerts.builder().changeThresholdPercent(threshold).marginFloorPercent(floor).changes(changes).lowMargin(low).build();
    }
}
