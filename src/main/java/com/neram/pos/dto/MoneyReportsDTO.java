package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Daily close report, budget against actual spending, staff performance and tips. */
public final class MoneyReportsDTO {
    private MoneyReportsDTO() {}

    // ---- budget ----
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BudgetRow {
        private String category; private String label;
        /** null when no budget was set */
        private Double budget; private double actual;
        private Double remaining; private Double usedPercent; private double projected;
        /** NONE, OK, WATCH, OVER */
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Budget {
        private int year; private int month; private int daysElapsed; private int daysInMonth;
        private List<BudgetRow> rows; private BudgetRow total;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BudgetUpdate { private String category; private Double monthlyAmount; }

    // ---- staff and tips ----
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StaffRow {
        private Long staffId; private String staff;
        private int orders; private double sales; private double avgOrder; private double itemsPerOrder;
        private double discounts; private double refunds;
        private double tips;
        /** tips handed over in the period */
        private double tipsPaid;
        /** tips received on their bills, all time, minus everything handed over */
        private double tipsOwed;
        private double salesShare;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StaffReport {
        private LocalDate start; private LocalDate end;
        private List<StaffRow> staff;
        private double totalSales; private int totalOrders; private double totalTips; private double totalTipsOwed;
        /** the total tips split equally between the staff who took orders (a suggestion for a shared tip box) */
        private double equalShare;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Payout { private Long id; private Long staffId; private String staffName; private double amount; private LocalDate paidOn; private String note; private String createdBy; }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PayoutRequest { private Long staffId; private Double amount; private String note; }

    // ---- daily close ----
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ShiftLine {
        private Long id; private String cashier; private LocalDateTime openedAt; private LocalDateTime closedAt; private String status;
        private double openingFloat; private double cashSales; private Double expectedCash; private Double countedCash; private Double variance;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class NameAmount { private String name; private double amount; private int count; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyClose {
        private LocalDate date;
        private double sales; private int orders; private double avgBill;
        private double previousDaySales; private Double changePercent;
        private List<NameAmount> payments;
        private double discounts; private double refunds; private int refundCount; private int voidedCount; private double voidedValue; private int cancelledCount;
        private double gst; private double tips;
        private double expenses; private String topExpense;
        private List<ShiftLine> shifts;
        private double cashVarianceTotal; private int openShifts;
        private int lowStock; private int belowZero; private int expiringSoon; private int expired; private double wasteCost;
        private List<NameAmount> topDishes; private String busiestHour;
        private List<NameAmount> topStaff;
        private List<String> overBudget;
        /** plain text used for Telegram */
        private String text;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyCloseSettings { private boolean enabled; private String time; }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SendResult { private int sent; private int failed; private int recipients; private String message; }
}
