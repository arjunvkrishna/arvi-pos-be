package com.neram.pos.service;

import com.neram.pos.dto.OwnerDashboardDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;
import com.neram.pos.entity.BillPayment;
import com.neram.pos.entity.DiningTable;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.repository.BillPaymentRepository;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.DiningTableRepository;
import com.neram.pos.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerDashboardService {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final DiningTableRepository diningTableRepository;
    private final InventoryItemRepository inventoryItemRepository;

    /** The parts of the bills that were paid in more than one way (cash + UPI...), by bill id. */
    private Map<Long, List<BillPayment>> partsOf(List<Bill> bills) {
        List<Long> ids = bills.stream().filter(b -> b.getPaymentSummary() != null).map(Bill::getId).collect(Collectors.toList());
        if (ids.isEmpty()) return Map.of();
        return billPaymentRepository.findByBillIdIn(ids).stream().collect(Collectors.groupingBy(BillPayment::getBillId));
    }

    public static LocalDate currentBusinessDate() {
        return com.neram.pos.util.BusinessDate.now();
    }

    @Transactional(readOnly = true)
    public OwnerDashboardDTO getDashboardMetrics(LocalDate date) {
        LocalDate today = date != null ? date : currentBusinessDate();
        LocalDate yesterday = today.minusDays(1);

        // 1. Fetch Paid Bills for Today and Yesterday (strictly excluding soft-deleted)
        List<Bill> todayBills = billRepository.findPaidByBusinessDate(today).stream()
                .filter(b -> b.getDeletedAt() == null && b.getStatus() == Bill.Status.PAID)
                .collect(Collectors.toList());

        List<Bill> yesterdayBills = billRepository.findPaidByBusinessDate(yesterday).stream()
                .filter(b -> b.getDeletedAt() == null && b.getStatus() == Bill.Status.PAID)
                .collect(Collectors.toList());

        // 2. Compute Today Financials
        BigDecimal grossSalesToday = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal netSalesToday = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountsToday = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxToday = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> tenderBreakdown = new LinkedHashMap<>();
        Map<String, TopDishAccumulator> dishSalesMap = new HashMap<>();
        Map<Long, List<BillPayment>> paidInParts = partsOf(todayBills);

        for (Bill b : todayBills) {
            BigDecimal net = b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO;
            BigDecimal sub = b.getSubtotal() != null ? b.getSubtotal() : BigDecimal.ZERO;
            BigDecimal disc = b.getDiscountAmount() != null ? b.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal tax = b.getTaxAmount() != null ? b.getTaxAmount() : BigDecimal.ZERO;

            netSalesToday = netSalesToday.add(net);
            grossSalesToday = grossSalesToday.add(sub.add(tax));
            discountsToday = discountsToday.add(disc);
            taxToday = taxToday.add(tax);

            List<BillPayment> parts = paidInParts.get(b.getId());
            if (parts != null && !parts.isEmpty()) {
                for (BillPayment p : parts) tenderBreakdown.merge(p.getMode(), p.getAmount(), BigDecimal::add);   // cash + UPI: each part under its own mode
            } else {
                String mode = b.getPaymentMode() != null ? b.getPaymentMode().name() : "CASH";
                tenderBreakdown.merge(mode, net, BigDecimal::add);
            }

            if (b.getItems() != null) {
                for (BillItem item : b.getItems()) {
                    String name = item.getItemName() != null ? item.getItemName() : "Unknown";
                    long qty = item.getQuantity() != null ? item.getQuantity() : 0;
                    BigDecimal itemSub = item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO;

                    dishSalesMap.computeIfAbsent(name, k -> new TopDishAccumulator(name))
                            .add(qty, itemSub);
                }
            }
        }

        long countToday = todayBills.size();
        BigDecimal avgOrderValueToday = countToday > 0
                ? netSalesToday.divide(BigDecimal.valueOf(countToday), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 3. Compute Yesterday Financials
        BigDecimal grossSalesYesterday = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal netSalesYesterday = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Bill b : yesterdayBills) {
            BigDecimal net = b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO;
            BigDecimal sub = b.getSubtotal() != null ? b.getSubtotal() : BigDecimal.ZERO;
            BigDecimal tax = b.getTaxAmount() != null ? b.getTaxAmount() : BigDecimal.ZERO;

            netSalesYesterday = netSalesYesterday.add(net);
            grossSalesYesterday = grossSalesYesterday.add(sub.add(tax));
        }

        long countYesterday = yesterdayBills.size();
        BigDecimal avgOrderValueYesterday = countYesterday > 0
                ? netSalesYesterday.divide(BigDecimal.valueOf(countYesterday), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 4. Comparison Growth Deltas
        Double netSalesGrowthPercent = 0.0;
        if (netSalesYesterday.compareTo(BigDecimal.ZERO) > 0) {
            netSalesGrowthPercent = netSalesToday.subtract(netSalesYesterday)
                    .divide(netSalesYesterday, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        } else if (netSalesToday.compareTo(BigDecimal.ZERO) > 0) {
            netSalesGrowthPercent = 100.0;
        }

        Double countGrowthPercent = 0.0;
        if (countYesterday > 0) {
            countGrowthPercent = ((double) (countToday - countYesterday) / countYesterday) * 100.0;
        } else if (countToday > 0) {
            countGrowthPercent = 100.0;
        }

        // 5. Live Table Occupancy
        List<DiningTable> activeTables = diningTableRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        int totalTables = activeTables.size();
        List<Bill> openBills = billRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(Bill.Status.ACTIVE, Bill.Status.KOT_REQUESTED, Bill.Status.KOT_PRINTED, Bill.Status.BILL_PRINTED, Bill.Status.BILL_REQUESTED));

        Set<String> occupiedTableNames = openBills.stream()
                .map(Bill::getTableNumber)
                .filter(t -> t != null && !t.isBlank() && !t.equalsIgnoreCase("Takeaway"))
                .collect(Collectors.toSet());

        int activeOccupied = occupiedTableNames.size();
        double occupancyPercent = totalTables > 0 ? ((double) activeOccupied / totalTables) * 100.0 : 0.0;

        // 6. Real-Time Low Stock Alerts (based on reorder_level or minQuantity)
        List<InventoryItem> allInventory = inventoryItemRepository.findByActiveTrue();
        List<OwnerDashboardDTO.LowStockItemDTO> lowStockAlerts = new ArrayList<>();

        for (InventoryItem inv : allInventory) {
            boolean isLow = false;
            double cur = inv.getQuantity() != null ? inv.getQuantity() : 0.0;
            double reorder = inv.getReorderLevel() != null ? inv.getReorderLevel().doubleValue() : 0.0;
            double min = inv.getMinQuantity() != null ? inv.getMinQuantity() : 0.0;

            if (reorder > 0.0 && cur <= reorder) {
                isLow = true;
            } else if (reorder <= 0.0 && cur <= min) {
                isLow = true;
            }

            if (isLow) {
                lowStockAlerts.add(OwnerDashboardDTO.LowStockItemDTO.builder()
                        .id(inv.getId())
                        .name(inv.getName())
                        .currentStock(cur)
                        .reorderLevel(reorder > 0.0 ? reorder : min)
                        .unit(inv.getUnit())
                        .build());
            }
        }

        // 7. Top 5 Selling Dishes Today
        List<OwnerDashboardDTO.TopSellingDishDTO> topSellingDishes = dishSalesMap.values().stream()
                .sorted((a, b) -> Long.compare(b.quantitySold, a.quantitySold))
                .limit(5)
                .map(d -> OwnerDashboardDTO.TopSellingDishDTO.builder()
                        .itemName(d.name)
                        .quantitySold(d.quantitySold)
                        .totalSales(d.totalSales.setScale(2, RoundingMode.HALF_UP))
                        .build())
                .collect(Collectors.toList());

        return OwnerDashboardDTO.builder()
                .businessDate(today)
                .grossSalesToday(grossSalesToday)
                .netSalesToday(netSalesToday)
                .totalDiscountsToday(discountsToday)
                .totalTaxToday(taxToday)
                .billsCountToday(countToday)
                .avgOrderValueToday(avgOrderValueToday)
                .grossSalesYesterday(grossSalesYesterday)
                .netSalesYesterday(netSalesYesterday)
                .billsCountYesterday(countYesterday)
                .avgOrderValueYesterday(avgOrderValueYesterday)
                .netSalesGrowthPercent(netSalesGrowthPercent)
                .billsCountGrowthPercent(countGrowthPercent)
                .tenderBreakdown(tenderBreakdown)
                .activeTablesOccupied(activeOccupied)
                .totalTables(totalTables)
                .tableOccupancyPercent(Math.round(occupancyPercent * 10.0) / 10.0)
                .lowStockItemsCount(lowStockAlerts.size())
                .lowStockAlerts(lowStockAlerts)
                .topSellingDishes(topSellingDishes)
                .build();
    }

    private static class TopDishAccumulator {
        String name;
        long quantitySold = 0;
        BigDecimal totalSales = BigDecimal.ZERO;

        TopDishAccumulator(String name) {
            this.name = name;
        }

        void add(long qty, BigDecimal sales) {
            this.quantitySold += qty;
            this.totalSales = this.totalSales.add(sales);
        }
    }
}
