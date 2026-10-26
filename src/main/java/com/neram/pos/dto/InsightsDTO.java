package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** "Insights" for the analytics screen: how the period compares, when and how people buy, what earns money, where money is lost. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightsDTO {
    private LocalDate start;
    private LocalDate end;
    private LocalDate prevStart;
    private LocalDate prevEnd;
    private Compare compare;
    private List<HourPoint> byHour;
    private List<WeekdayPoint> byWeekday;
    /** sales per weekday (0 = Monday) per hour (0..23) */
    private List<List<Double>> heatmap;
    private List<Slice> byOrderType;
    private List<Slice> byPaymentMode;
    private List<Slice> byCategory;
    private List<StaffRow> byStaff;
    private List<TableRow> byTable;
    private Discounts discounts;
    private Losses losses;
    private Tax tax;
    private List<DishRow> dishes;
    private List<NameRow> neverSold;
    private int neverSoldCount;
    private Stock stock;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Metric { private double current; private double previous; /** null when there was nothing to compare with */ private Double changePercent; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Compare { private Metric sales; private Metric orders; private Metric avgOrder; private Metric itemsPerOrder; private Metric discounts; private Metric refunds; private Metric voids; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HourPoint { private int hour; private double sales; private int orders; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WeekdayPoint { private int day; private String label; private double sales; private int orders; private double avgOrder; /** how many such days fell in the period */ private int days; private double salesPerDay; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Slice { private String label; private int orders; private double amount; private int quantity; private double share; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StaffRow { private String staff; private int orders; private double sales; private double avgOrder; private double share; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TableRow { private String table; private int orders; private double sales; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Discounts { private double discountAmount; private double promotionAmount; private double total; private int billsWithDiscount; private double percentOfGross; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Losses { private double refundAmount; private int refundCount; private int voidedCount; private double voidedValue; private int cancelledCount; private double wasteCost; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Tax { private double taxableSales; private double cgst; private double sgst; private double totalTax; private double roundOff; }

    /** One dish: what it sold, what it cost to make (when a recipe is known) and where it sits in the menu matrix. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DishRow {
        private Long menuItemId; private String name; private String category; private int quantity; private double revenue;
        /** null = no recipe, so the cost is not known */
        private Double costPerServing; private Double cost; private Double profit; private Double marginPercent;
        /** STAR, PLOWHORSE, PUZZLE, DOG, or null when the cost is not known */
        private String quadrant;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class NameRow { private String name; private String category; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Stock {
        private List<Slice> valueByCategory; private double totalValue; private int negativeCount; private int lowCount; private int expiringCount; private int expiredCount;
        private List<Slice> wasteByReason; private double wasteCost;
    }
}
