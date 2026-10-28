package com.neram.pos.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.neram.pos.dto.AccountingDTO;
import com.neram.pos.entity.*;
import com.neram.pos.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final CafeExpenseRepository cafeExpenseRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final GiftCardRepository giftCardRepository;
    private final CustomerRepository customerRepository;
    private final LoyaltyLedgerRepository loyaltyLedgerRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final AccountingService accountingService;

    @Data
    @Builder
    public static class TabularReport {
        private String reportId;
        private String title;
        private String periodDescription;
        private List<String> headers;
        private List<List<String>> rows;
        private List<String> footerTotals;
    }

    public byte[] generateReport(String reportId, String format, Map<String, Object> filterParams) {
        LocalDate startDate = parseDateParam(filterParams, "startDate", LocalDate.now().withDayOfMonth(1));
        LocalDate endDate = parseDateParam(filterParams, "endDate", LocalDate.now());

        TabularReport data = buildReportData(reportId, startDate, endDate);

        String fmt = format != null ? format.toUpperCase() : "CSV";
        switch (fmt) {
            case "XLSX":
                return renderExcel(data);
            case "PDF":
                return renderPdf(data);
            case "CSV":
            default:
                return renderCsv(data);
        }
    }

    private TabularReport buildReportData(String reportId, LocalDate start, LocalDate end) {
        switch (reportId.toLowerCase()) {
            case "sales_summary":
                return buildSalesSummary(start, end);
            case "daily_close":
                return buildDailyCloseReport(start, end);
            case "profit_and_loss":
                return buildProfitAndLossReport(start, end);
            case "balance_sheet":
                return buildBalanceSheetReport();
            case "gst_summary":
                return buildGstSummary(start, end);
            case "food_cost":
                return buildFoodCostReport(start, end);
            case "top_selling_dishes":
                return buildTopDishes(start, end);
            case "slow_moving_dishes":
                return buildSlowDishes(start, end);
            case "item_modifiers_sales":
                return buildModifiersReport(start, end);
            case "combo_meals_sales":
                return buildCombosReport(start, end);
            case "customer_retention":
                return buildCustomerRetention(start, end);
            case "loyalty_ledger":
                return buildLoyaltyLedgerReport(start, end);
            case "gift_cards_summary":
                return buildGiftCardsReport();
            case "discount_summary":
                return buildDiscountReport(start, end);
            case "operating_expenses":
                return buildOperatingExpensesReport(start, end);
            case "supplier_procurement":
                return buildSupplierProcurementReport(start, end);
            case "staff_performance":
                return buildStaffPerformanceReport(start, end);
            case "peak_hours_heatmap":
                return buildPeakHoursHeatmap(start, end);
            default:
                return buildSalesSummary(start, end);
        }
    }

    // ──────────────── 1. Sales Summary ────────────────
    private TabularReport buildSalesSummary(LocalDate start, LocalDate end) {
        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(start, end);
        Map<LocalDate, List<Bill>> byDate = bills.stream()
                .filter(b -> b.getBusinessDate() != null)
                .collect(Collectors.groupingBy(Bill::getBusinessDate, TreeMap::new, Collectors.toList()));

        List<List<String>> rows = new ArrayList<>();
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalGst = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        int totalOrders = 0;

        for (Map.Entry<LocalDate, List<Bill>> entry : byDate.entrySet()) {
            LocalDate d = entry.getKey();
            List<Bill> dayBills = entry.getValue();
            int orders = dayBills.size();
            totalOrders += orders;

            BigDecimal sales = dayBills.stream().map(b -> b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal gst = dayBills.stream().map(b -> b.getTaxAmount() != null ? b.getTaxAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tips = dayBills.stream().map(b -> b.getTipAmount() != null ? b.getTipAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);

            totalSales = totalSales.add(sales);
            totalGst = totalGst.add(gst);
            totalTips = totalTips.add(tips);

            rows.add(List.of(
                    d.toString(),
                    String.valueOf(orders),
                    sales.setScale(2, RoundingMode.HALF_UP).toString(),
                    gst.setScale(2, RoundingMode.HALF_UP).toString(),
                    tips.setScale(2, RoundingMode.HALF_UP).toString(),
                    sales.add(tips).setScale(2, RoundingMode.HALF_UP).toString()
            ));
        }

        return TabularReport.builder()
                .reportId("sales_summary")
                .title("Sales Summary Report")
                .periodDescription(start + " to " + end)
                .headers(List.of("Business Date", "Orders Count", "Net Sales (₹)", "GST (₹)", "Tips (₹)", "Total Collected (₹)"))
                .rows(rows)
                .footerTotals(List.of("TOTAL", String.valueOf(totalOrders), totalSales.toPlainString(), totalGst.toPlainString(), totalTips.toPlainString(), totalSales.add(totalTips).toPlainString()))
                .build();
    }

    // ──────────────── 2. Daily Close Report ────────────────
    private TabularReport buildDailyCloseReport(LocalDate start, LocalDate end) {
        List<CashierShift> shifts = cashierShiftRepository.findAll().stream()
                .filter(s -> s.getOpenedAt() != null && !s.getOpenedAt().toLocalDate().isBefore(start) && !s.getOpenedAt().toLocalDate().isAfter(end))
                .collect(Collectors.toList());

        List<List<String>> rows = new ArrayList<>();
        for (CashierShift s : shifts) {
            BigDecimal diff = s.getVariance() != null ? s.getVariance() : BigDecimal.ZERO;
            rows.add(List.of(
                    s.getId().toString(),
                    s.getOpenedAt() != null ? s.getOpenedAt().toLocalDate().toString() : "-",
                    s.getCashierName() != null ? s.getCashierName() : "-",
                    s.getOpeningFloat() != null ? s.getOpeningFloat().toPlainString() : "0.00",
                    s.getCashSales() != null ? s.getCashSales().toPlainString() : "0.00",
                    s.getGiftCardCashSales() != null ? s.getGiftCardCashSales().toPlainString() : "0.00",
                    s.getCashOutTotal() != null ? s.getCashOutTotal().toPlainString() : "0.00",
                    s.getCountedCash() != null ? s.getCountedCash().toPlainString() : "0.00",
                    diff.toPlainString(),
                    s.getStatus() != null ? s.getStatus().name() : "-"
            ));
        }

        return TabularReport.builder()
                .reportId("daily_close")
                .title("Daily Close & Cash Drawer Variance Report")
                .periodDescription(start + " to " + end)
                .headers(List.of("Shift #", "Date", "Cashier", "Float (₹)", "Cash Sales (₹)", "GC Cash (₹)", "Expenses (₹)", "Actual Cash (₹)", "Variance (₹)", "Status"))
                .rows(rows)
                .build();
    }

    // ──────────────── 3. Profit & Loss Report ────────────────
    private TabularReport buildProfitAndLossReport(LocalDate start, LocalDate end) {
        AccountingDTO.ProfitAndLossReport pl = accountingService.getProfitAndLoss(start, end);
        List<List<String>> rows = List.of(
                List.of("Gross Sales", pl.getGrossSales().toPlainString(), "Gross bill net revenue"),
                List.of("Less: GST Collected", pl.getGstCollected().toPlainString(), "CGST + SGST statutory tax"),
                List.of("Less: Tips Collected", pl.getTipsCollected().toPlainString(), "Disbursed staff tips"),
                List.of("= Net Sales Revenue", pl.getNetSalesRevenue().toPlainString(), "Core revenue"),
                List.of("Less: Cost of Goods Sold (COGS)", pl.getCogs().toPlainString(), "Supplier invoices / ingredient procurement"),
                List.of("Less: Loyalty Points Redeemed", pl.getLoyaltyPointsRedeemed().toPlainString(), "Points tender discount"),
                List.of("= Gross Profit", pl.getGrossProfit().toPlainString(), "Revenue minus product costs"),
                List.of("Less: Operating Expenses (OpEx)", pl.getOperatingExpenses().toPlainString(), "Rent, utilities, marketing, maintenance"),
                List.of("Less: Payroll Disbursed", pl.getSalariesPaid().toPlainString(), "Staff salary payments"),
                List.of("= Net Profit / (Loss)", pl.getNetProfit().toPlainString(), "Final bottom line")
        );

        return TabularReport.builder()
                .reportId("profit_and_loss")
                .title("Statutory Profit & Loss Statement")
                .periodDescription(start + " to " + end)
                .headers(List.of("Accounting Measure", "Amount (₹)", "Description"))
                .rows(rows)
                .build();
    }

    // ──────────────── 4. Balance Sheet Report ────────────────
    private TabularReport buildBalanceSheetReport() {
        AccountingDTO.BalanceSheetReport bs = accountingService.getBalanceSheet();
        List<List<String>> rows = List.of(
                List.of("ASSETS", "Cash in Drawers & Operating Cash", bs.getAssets().getCashInDrawerAndBank().toPlainString()),
                List.of("ASSETS", "Inventory Valuation (At Cost)", bs.getAssets().getInventoryValuationAtCost().toPlainString()),
                List.of("TOTAL ASSETS", "-", bs.getAssets().getTotalAssets().toPlainString()),
                List.of("LIABILITIES", "Unpaid Supplier Invoices", bs.getLiabilities().getUnpaidSupplierInvoices().toPlainString()),
                List.of("LIABILITIES", "Active Gift Card Liability", bs.getLiabilities().getGiftCardLiability().toPlainString()),
                List.of("LIABILITIES", "Unpaid Staff Tips", bs.getLiabilities().getUnpaidTips().toPlainString()),
                List.of("TOTAL LIABILITIES", "-", bs.getLiabilities().getTotalLiabilities().toPlainString()),
                List.of("EQUITY", "Net Worth / Owners Equity", bs.getNetWorthOrEquity().toPlainString())
        );

        return TabularReport.builder()
                .reportId("balance_sheet")
                .title("Management Balance Sheet Snapshot")
                .periodDescription("As of " + bs.getAsOfDate())
                .headers(List.of("Classification", "Item Account", "Balance (₹)"))
                .rows(rows)
                .build();
    }

    // ──────────────── 5. GST Summary ────────────────
    private TabularReport buildGstSummary(LocalDate start, LocalDate end) {
        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(start, end);
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;

        for (Bill b : bills) {
            BigDecimal tax = b.getTaxAmount() != null ? b.getTaxAmount() : BigDecimal.ZERO;
            BigDecimal net = b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO;
            BigDecimal taxable = net.subtract(tax);
            if (taxable.compareTo(BigDecimal.ZERO) < 0) taxable = BigDecimal.ZERO;

            BigDecimal halfTax = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            totalTaxable = totalTaxable.add(taxable);
            totalCgst = totalCgst.add(halfTax);
            totalSgst = totalSgst.add(tax.subtract(halfTax));
        }

        List<List<String>> rows = List.of(
                List.of("GST @ 5% (Restaurant)", totalTaxable.toPlainString(), totalCgst.toPlainString(), totalSgst.toPlainString(), totalCgst.add(totalSgst).toPlainString())
        );

        return TabularReport.builder()
                .reportId("gst_summary")
                .title("GST / Tax Summary Report")
                .periodDescription(start + " to " + end)
                .headers(List.of("Tax Slab", "Taxable Turnover (₹)", "CGST (₹)", "SGST (₹)", "Total Tax (₹)"))
                .rows(rows)
                .build();
    }

    // ──────────────── 6. Food Cost Report ────────────────
    private TabularReport buildFoodCostReport(LocalDate start, LocalDate end) {
        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(start, end);
        BigDecimal netSales = bills.stream()
                .map(b -> b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SupplierInvoice> invoices = supplierInvoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDesc(start, end);
        BigDecimal foodPurchases = invoices.stream()
                .map(i -> i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double foodCostPct = netSales.compareTo(BigDecimal.ZERO) > 0
                ? (foodPurchases.doubleValue() / netSales.doubleValue()) * 100.0
                : 0.0;

        List<List<String>> rows = List.of(
                List.of("Total Net Sales", netSales.toPlainString()),
                List.of("Total Ingredient & Food Procurement", foodPurchases.toPlainString()),
                List.of("Food Cost Percentage", String.format("%.2f%%", foodCostPct))
        );

        return TabularReport.builder()
                .reportId("food_cost")
                .title("Food Cost Percentage Analysis")
                .periodDescription(start + " to " + end)
                .headers(List.of("Metric", "Amount (₹) / Ratio"))
                .rows(rows)
                .build();
    }

    // ──────────────── 7 & 8. Top & Slow Moving Dishes ────────────────
    private TabularReport buildTopDishes(LocalDate start, LocalDate end) {
        List<Object[]> results = billItemRepository.findDishSalesBetweenBusinessDates(start, end);
        List<List<String>> rows = new ArrayList<>();
        int rank = 1;
        for (Object[] row : results) {
            String name = (String) row[1];
            Long qty = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            Double rev = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            rows.add(List.of(String.valueOf(rank++), name, String.valueOf(qty), String.format("%.2f", rev)));
            if (rank > 20) break;
        }

        return TabularReport.builder()
                .reportId("top_selling_dishes")
                .title("Top Selling Dishes Leaderboard")
                .periodDescription(start + " to " + end)
                .headers(List.of("Rank", "Dish Name", "Quantity Sold", "Total Revenue (₹)"))
                .rows(rows)
                .build();
    }

    private TabularReport buildSlowDishes(LocalDate start, LocalDate end) {
        List<Object[]> results = billItemRepository.findDishSalesBetweenBusinessDates(start, end);
        List<List<String>> rows = new ArrayList<>();
        if (results.size() > 5) {
            List<Object[]> sub = new ArrayList<>(results.subList(Math.max(results.size() - 20, 0), results.size()));
            Collections.reverse(sub);
            int rank = 1;
            for (Object[] row : sub) {
                String name = (String) row[1];
                Long qty = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                Double rev = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
                rows.add(List.of(String.valueOf(rank++), name, String.valueOf(qty), String.format("%.2f", rev)));
            }
        }

        return TabularReport.builder()
                .reportId("slow_moving_dishes")
                .title("Slow Moving Dishes Report")
                .periodDescription(start + " to " + end)
                .headers(List.of("Rank", "Dish Name", "Quantity Sold", "Total Revenue (₹)"))
                .rows(rows)
                .build();
    }

    // ──────────────── 9 & 10. Modifiers & Combos ────────────────
    private TabularReport buildModifiersReport(LocalDate start, LocalDate end) {
        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(start, end);
        Map<String, Integer> modCounts = new HashMap<>();
        for (Bill b : bills) {
            for (BillItem item : b.getItems()) {
                if (item.getModifiersSnapshot() != null && !item.getModifiersSnapshot().isBlank()) {
                    modCounts.put("Modifier Add-ons", modCounts.getOrDefault("Modifier Add-ons", 0) + item.getQuantity());
                }
            }
        }
        List<List<String>> rows = new ArrayList<>();
        modCounts.forEach((k, v) -> rows.add(List.of(k, String.valueOf(v))));
        if (rows.isEmpty()) rows.add(List.of("Modifiers", "0"));

        return TabularReport.builder()
                .reportId("item_modifiers_sales")
                .title("Item Modifiers & Add-ons Sales Report")
                .periodDescription(start + " to " + end)
                .headers(List.of("Add-on Item", "Attachment Count"))
                .rows(rows)
                .build();
    }

    private TabularReport buildCombosReport(LocalDate start, LocalDate end) {
        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(start, end);
        int comboCount = 0;
        for (Bill b : bills) {
            for (BillItem item : b.getItems()) {
                if (item.getComboSnapshot() != null && !item.getComboSnapshot().isBlank()) {
                    comboCount += item.getQuantity();
                }
            }
        }
        List<List<String>> rows = List.of(List.of("Combo Meals Sold", String.valueOf(comboCount)));

        return TabularReport.builder()
                .reportId("combo_meals_sales")
                .title("Combo Meals & Selections Analysis")
                .periodDescription(start + " to " + end)
                .headers(List.of("Bundle Category", "Total Portions Sold"))
                .rows(rows)
                .build();
    }

    // ──────────────── 11 & 12. Customers & Loyalty ────────────────
    private TabularReport buildCustomerRetention(LocalDate start, LocalDate end) {
        long totalCustomers = customerRepository.count();
        long withConsent = customerRepository.findByMarketingConsentTrueAndIsActiveTrue().size();
        List<List<String>> rows = List.of(
                List.of("Total Registered Customers", String.valueOf(totalCustomers)),
                List.of("Customers with Marketing Consent", String.valueOf(withConsent)),
                List.of("Marketing Reach Rate", String.format("%.1f%%", totalCustomers > 0 ? (withConsent * 100.0 / totalCustomers) : 0.0))
        );

        return TabularReport.builder()
                .reportId("customer_retention")
                .title("Customer Retention & CRM Summary")
                .periodDescription(start + " to " + end)
                .headers(List.of("Metric", "Value"))
                .rows(rows)
                .build();
    }

    private TabularReport buildLoyaltyLedgerReport(LocalDate start, LocalDate end) {
        List<LoyaltyLedger> entries = loyaltyLedgerRepository.findAll().stream()
                .sorted(Comparator.comparing(LoyaltyLedger::getCreatedAt).reversed())
                .limit(50)
                .collect(Collectors.toList());

        List<List<String>> rows = new ArrayList<>();
        for (LoyaltyLedger l : entries) {
            rows.add(List.of(
                    l.getId().toString(),
                    l.getCustomerId() != null ? "Customer #" + l.getCustomerId() : "-",
                    l.getType() != null ? l.getType() : "-",
                    String.valueOf(l.getPoints()),
                    String.valueOf(l.getBalanceAfter()),
                    l.getCreatedAt() != null ? l.getCreatedAt().toString() : "-"
            ));
        }

        return TabularReport.builder()
                .reportId("loyalty_ledger")
                .title("Loyalty Points Audit Ledger")
                .periodDescription(start + " to " + end)
                .headers(List.of("Entry #", "Customer", "Type", "Points", "Balance After", "Timestamp"))
                .rows(rows)
                .build();
    }

    // ──────────────── 13 & 14. Gift Cards & Discounts ────────────────
    private TabularReport buildGiftCardsReport() {
        List<GiftCard> cards = giftCardRepository.findAll();
        List<List<String>> rows = new ArrayList<>();
        for (GiftCard g : cards) {
            rows.add(List.of(
                    g.getCode(),
                    g.getInitialBalance() != null ? g.getInitialBalance().toPlainString() : "0.00",
                    g.getCurrentBalance() != null ? g.getCurrentBalance().toPlainString() : "0.00",
                    g.getStatus() != null ? g.getStatus().name() : "-",
                    g.getCreatedAt() != null ? g.getCreatedAt().toString() : "-"
            ));
        }

        return TabularReport.builder()
                .reportId("gift_cards_summary")
                .title("Gift Cards Portfolio & Liability Report")
                .periodDescription("All Active and Historical Cards")
                .headers(List.of("Card Code", "Initial Value (₹)", "Current Balance (₹)", "Status", "Issued Date"))
                .rows(rows)
                .build();
    }

    private TabularReport buildDiscountReport(LocalDate start, LocalDate end) {
        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(start, end);
        BigDecimal manualDiscounts = bills.stream()
                .map(b -> b.getDiscountAmount() != null ? b.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promoDiscounts = bills.stream()
                .map(b -> b.getPromotionDiscountAmount() != null ? b.getPromotionDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal couponDiscounts = bills.stream()
                .map(b -> b.getCouponDiscount() != null ? b.getCouponDiscount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<List<String>> rows = List.of(
                List.of("Manual Item/Bill Discounts", manualDiscounts.toPlainString()),
                List.of("Automatic Promotions", promoDiscounts.toPlainString()),
                List.of("Marketing Coupon Discounts", couponDiscounts.toPlainString()),
                List.of("TOTAL DISCOUNTS CONCEDED", manualDiscounts.add(promoDiscounts).add(couponDiscounts).toPlainString())
        );

        return TabularReport.builder()
                .reportId("discount_summary")
                .title("Discounts, Promotions & Coupons Concession Report")
                .periodDescription(start + " to " + end)
                .headers(List.of("Discount Channel", "Total Conceded Amount (₹)"))
                .rows(rows)
                .build();
    }

    // ──────────────── 15. Operating Expenses ────────────────
    private TabularReport buildOperatingExpensesReport(LocalDate start, LocalDate end) {
        List<CafeExpense> expenses = cafeExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(start, end);
        List<List<String>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CafeExpense e : expenses) {
            BigDecimal amt = e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO;
            total = total.add(amt);
            rows.add(List.of(
                    e.getExpenseDate() != null ? e.getExpenseDate().toString() : "-",
                    e.getType() != null ? e.getType().name() : "MISC",
                    e.getDescription() != null ? e.getDescription() : "-",
                    e.getVendorOrPayee() != null ? e.getVendorOrPayee() : "-",
                    amt.toPlainString()
            ));
        }

        return TabularReport.builder()
                .reportId("operating_expenses")
                .title("Operating Expenses (OpEx) Audit Report")
                .periodDescription(start + " to " + end)
                .headers(List.of("Expense Date", "Category", "Description", "Payee", "Amount (₹)"))
                .rows(rows)
                .footerTotals(List.of("TOTAL OPEX", "-", "-", "-", total.toPlainString()))
                .build();
    }

    // ──────────────── 16. Supplier Procurement ────────────────
    private TabularReport buildSupplierProcurementReport(LocalDate start, LocalDate end) {
        List<SupplierInvoice> invoices = supplierInvoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDesc(start, end);
        List<List<String>> rows = new ArrayList<>();
        for (SupplierInvoice i : invoices) {
            rows.add(List.of(
                    i.getInvoiceNumber() != null ? i.getInvoiceNumber() : "-",
                    i.getSupplierId() != null ? "Supplier #" + i.getSupplierId() : "-",
                    i.getInvoiceDate() != null ? i.getInvoiceDate().toString() : "-",
                    i.getTotalAmount() != null ? i.getTotalAmount().toPlainString() : "0.00",
                    i.getOutstandingAmount() != null ? i.getOutstandingAmount().toPlainString() : "0.00",
                    i.getStatus() != null ? i.getStatus().name() : "-"
            ));
        }

        return TabularReport.builder()
                .reportId("supplier_procurement")
                .title("Supplier Procurement & Outstanding Invoices")
                .periodDescription(start + " to " + end)
                .headers(List.of("Invoice #", "Supplier", "Date", "Total (₹)", "Outstanding (₹)", "Status"))
                .rows(rows)
                .build();
    }

    // ──────────────── 17. Staff Performance ────────────────
    private TabularReport buildStaffPerformanceReport(LocalDate start, LocalDate end) {
        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(start, end);
        Map<Long, List<Bill>> byStaff = bills.stream()
                .filter(b -> b.getCreatedBy() != null)
                .collect(Collectors.groupingBy(Bill::getCreatedBy));

        List<List<String>> rows = new ArrayList<>();
        byStaff.forEach((staffId, sBills) -> {
            int orders = sBills.size();
            BigDecimal sales = sBills.stream().map(b -> b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tips = sBills.stream().map(b -> b.getTipAmount() != null ? b.getTipAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(List.of("Staff ID " + staffId, String.valueOf(orders), sales.toPlainString(), tips.toPlainString()));
        });

        return TabularReport.builder()
                .reportId("staff_performance")
                .title("Staff Billing Performance & Tips Attributed")
                .periodDescription(start + " to " + end)
                .headers(List.of("Staff Member", "Orders Processed", "Net Sales (₹)", "Tips Generated (₹)"))
                .rows(rows)
                .build();
    }

    // ──────────────── 18. Peak Hours Heatmap ────────────────
    private TabularReport buildPeakHoursHeatmap(LocalDate start, LocalDate end) {
        List<Bill> bills = billRepository.findPaidBillsBetweenBusinessDates(start, end);
        Map<Integer, List<Bill>> byHour = bills.stream()
                .filter(b -> b.getCreatedAt() != null)
                .collect(Collectors.groupingBy(b -> b.getCreatedAt().getHour(), TreeMap::new, Collectors.toList()));

        List<List<String>> rows = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            List<Bill> hBills = byHour.getOrDefault(h, Collections.emptyList());
            int orders = hBills.size();
            BigDecimal sales = hBills.stream().map(b -> b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            String timeSlot = String.format("%02d:00 - %02d:00", h, (h + 1) % 24);
            rows.add(List.of(timeSlot, String.valueOf(orders), sales.toPlainString()));
        }

        return TabularReport.builder()
                .reportId("peak_hours_heatmap")
                .title("Hourly Sales Heatmap & Traffic Distribution")
                .periodDescription(start + " to " + end)
                .headers(List.of("Hour Slot", "Orders Handled", "Total Revenue (₹)"))
                .rows(rows)
                .build();
    }

    // ──────────────── Export Renderers ────────────────

    public byte[] renderCsv(TabularReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.println("# " + report.getTitle());
            writer.println("# Period: " + report.getPeriodDescription());
            writer.println();

            writer.println(String.join(",", report.getHeaders().stream().map(ReportGenerationService::escapeCsv).collect(Collectors.toList())));
            for (List<String> row : report.getRows()) {
                writer.println(String.join(",", row.stream().map(ReportGenerationService::escapeCsv).collect(Collectors.toList())));
            }
            if (report.getFooterTotals() != null) {
                writer.println(String.join(",", report.getFooterTotals().stream().map(ReportGenerationService::escapeCsv).collect(Collectors.toList())));
            }
            writer.flush();
        }
        return out.toByteArray();
    }

    public byte[] renderExcel(TabularReport report) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.setCompressTempFiles(true);
            SXSSFSheet sheet = wb.createSheet("Report");
            sheet.setDisplayGridlines(true);

            CellStyle titleStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rIdx = 0;
            Row titleRow = sheet.createRow(rIdx++);
            titleRow.createCell(0).setCellValue(report.getTitle());
            titleRow.getCell(0).setCellStyle(titleStyle);

            Row metaRow = sheet.createRow(rIdx++);
            metaRow.createCell(0).setCellValue("Period: " + report.getPeriodDescription() + " | Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            rIdx++; // blank line

            Row headRow = sheet.createRow(rIdx++);
            for (int i = 0; i < report.getHeaders().size(); i++) {
                Cell c = headRow.createCell(i);
                c.setCellValue(report.getHeaders().get(i));
                c.setCellStyle(headerStyle);
            }

            for (List<String> rowData : report.getRows()) {
                Row r = sheet.createRow(rIdx++);
                for (int i = 0; i < rowData.size(); i++) {
                    r.createCell(i).setCellValue(rowData.get(i));
                }
            }

            if (report.getFooterTotals() != null) {
                Row totRow = sheet.createRow(rIdx++);
                for (int i = 0; i < report.getFooterTotals().size(); i++) {
                    Cell c = totRow.createCell(i);
                    c.setCellValue(report.getFooterTotals().get(i));
                }
            }

            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Excel report: {}", e.getMessage(), e);
            throw new RuntimeException("Excel export failed: " + e.getMessage(), e);
        }
    }

    public byte[] renderPdf(TabularReport report) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph titleP = new Paragraph(report.getTitle(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 41, 59)));
            Paragraph subP = new Paragraph("Period: " + report.getPeriodDescription() + " • Generated: " + LocalDate.now(), FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY));
            subP.setSpacingAfter(12f);
            document.add(titleP);
            document.add(subP);

            PdfPTable table = new PdfPTable(report.getHeaders().size());
            table.setWidthPercentage(100);
            table.setSpacingBefore(6f);

            for (String h : report.getHeaders()) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
                cell.setBackgroundColor(new Color(30, 41, 59));
                cell.setPadding(6f);
                cell.setBorder(Rectangle.NO_BORDER);
                table.addCell(cell);
            }

            for (List<String> row : report.getRows()) {
                for (String val : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(val != null ? val : "-", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY)));
                    cell.setPadding(5f);
                    cell.setBorderColor(new Color(226, 232, 240));
                    table.addCell(cell);
                }
            }

            if (report.getFooterTotals() != null) {
                for (String val : report.getFooterTotals()) {
                    PdfPCell cell = new PdfPCell(new Phrase(val != null ? val : "", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK)));
                    cell.setPadding(5f);
                    cell.setBackgroundColor(new Color(241, 245, 249));
                    table.addCell(cell);
                }
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("PDF export failed: " + e.getMessage(), e);
        }
    }

    static String escapeCsv(String val) {
        if (val == null) return "\"\"";
        // A text cell that starts like a formula would run when the file is opened in Excel (customer names, bank descriptions...)
        if (!val.isEmpty() && "=+-@\t\r".indexOf(val.charAt(0)) >= 0 && !val.matches("[-+]?[\\d,]*\\.?\\d+")) {
            val = "'" + val;
        }
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private LocalDate parseDateParam(Map<String, Object> params, String key, LocalDate fallback) {
        if (params == null || !params.containsKey(key)) return fallback;
        Object val = params.get(key);
        if (val instanceof String s && !s.isBlank()) {
            try {
                return LocalDate.parse(s.trim());
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}
