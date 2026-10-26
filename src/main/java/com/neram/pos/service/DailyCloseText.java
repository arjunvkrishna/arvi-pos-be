package com.neram.pos.service;

import com.neram.pos.dto.MoneyReportsDTO;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** The daily close report as a plain-text message (Telegram). Only lines that matter are written. */
public final class DailyCloseText {
    private DailyCloseText() {}

    static String hour(int h) { return (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " am" : " pm") + " - " + (((h + 1) % 24) % 12 == 0 ? 12 : ((h + 1) % 24) % 12) + (((h + 1) % 24) < 12 ? " am" : " pm"); }

    static String money(double v) { return "₹" + String.format(Locale.US, "%,.0f", v); }
    static String signed(double v) { return (v > 0 ? "+" : v < 0 ? "-" : "") + money(Math.abs(v)); }

    public static String format(MoneyReportsDTO.DailyClose d) {
        StringBuilder t = new StringBuilder();
        t.append("NERAM daily close - ").append(d.getDate().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH))).append('\n');
        t.append("\nSales ").append(money(d.getSales())).append(" from ").append(d.getOrders()).append(d.getOrders() == 1 ? " order" : " orders");
        if (d.getOrders() > 0) t.append(", average bill ").append(money(d.getAvgBill()));
        t.append('\n');
        if (d.getChangePercent() != null) t.append((d.getChangePercent() >= 0 ? "Up " : "Down ")).append(Math.abs(d.getChangePercent())).append("% on the day before (").append(money(d.getPreviousDaySales())).append(")\n");
        else if (d.getPreviousDaySales() == 0 && d.getSales() > 0) t.append("Nothing sold the day before.\n");
        if (!d.getPayments().isEmpty()) t.append("Paid by: ").append(join(d.getPayments())).append('\n');
        if (d.getTopDishes() != null && !d.getTopDishes().isEmpty()) t.append("Top dishes: ").append(d.getTopDishes().stream().map(x -> x.getName() + " x" + x.getCount()).reduce((a, b) -> a + ", " + b).orElse("")).append('\n');
        if (d.getBusiestHour() != null) t.append("Busiest hour: ").append(d.getBusiestHour()).append('\n');

        StringBuilder loss = new StringBuilder();
        if (d.getDiscounts() > 0) loss.append("\nDiscounts ").append(money(d.getDiscounts()));
        if (d.getRefundCount() > 0) loss.append("\nRefunds ").append(money(d.getRefunds())).append(" (").append(d.getRefundCount()).append(')');
        if (d.getVoidedCount() > 0) loss.append("\nVoided orders ").append(d.getVoidedCount()).append(" worth ").append(money(d.getVoidedValue()));
        if (d.getCancelledCount() > 0) loss.append("\nCancelled orders ").append(d.getCancelledCount());
        if (d.getWasteCost() > 0) loss.append("\nStock wasted ").append(money(d.getWasteCost()));
        if (loss.length() > 0) t.append("\nLosses").append(loss).append('\n');

        t.append("\nCash drawer\n");
        if (d.getShifts().isEmpty()) t.append("No shift today.\n");
        for (MoneyReportsDTO.ShiftLine s : d.getShifts()) {
            t.append("- ").append(s.getCashier()).append(": ");
            if ("OPEN".equals(s.getStatus())) t.append("still open, expected cash ").append(s.getExpectedCash() != null ? money(s.getExpectedCash()) : "-").append('\n');
            else t.append("expected ").append(money(s.getExpectedCash() != null ? s.getExpectedCash() : 0)).append(", counted ").append(money(s.getCountedCash() != null ? s.getCountedCash() : 0))
                    .append(s.getVariance() != null && Math.abs(s.getVariance()) >= 1 ? " (" + (s.getVariance() < 0 ? "SHORT " : "EXTRA ") + money(Math.abs(s.getVariance())) + ")" : " (matches)").append('\n');
        }
        if (d.getOpenShifts() > 0) t.append("A shift is still open.\n");

        t.append("\nMoney out: expenses ").append(money(d.getExpenses())).append(d.getTopExpense() != null ? " (most on " + d.getTopExpense() + ")" : "").append('\n');
        if (d.getTips() > 0) t.append("Tips received ").append(money(d.getTips())).append('\n');
        if (d.getGst() > 0) t.append("GST collected ").append(money(d.getGst())).append('\n');

        StringBuilder stock = new StringBuilder();
        if (d.getLowStock() > 0) stock.append("\n").append(d.getLowStock()).append(" low");
        if (d.getBelowZero() > 0) stock.append("\n").append(d.getBelowZero()).append(" below zero (purchase not entered)");
        if (d.getExpired() > 0) stock.append("\n").append(d.getExpired()).append(" expired");
        if (d.getExpiringSoon() > 0) stock.append("\n").append(d.getExpiringSoon()).append(" expiring soon");
        if (stock.length() > 0) t.append("\nStock to look at").append(stock).append('\n');
        if (d.getOverBudget() != null && !d.getOverBudget().isEmpty()) t.append("\nOver budget this month: ").append(String.join(", ", d.getOverBudget())).append('\n');
        if (d.getTopStaff() != null && !d.getTopStaff().isEmpty()) t.append("\nTop staff: ").append(d.getTopStaff().stream().map(x -> x.getName() + " " + money(x.getAmount())).reduce((a, b) -> a + ", " + b).orElse("")).append('\n');
        return t.toString().stripTrailing();
    }

    private static String join(List<MoneyReportsDTO.NameAmount> list) {
        return list.stream().map(x -> x.getName() + " " + money(x.getAmount())).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
