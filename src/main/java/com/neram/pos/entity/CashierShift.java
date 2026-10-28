package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "cashier_shifts")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class CashierShift implements com.neram.pos.tenancy.TenantScoped , com.neram.pos.access.OutletScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "register_id", nullable = false, length = 100)
    @Builder.Default
    private String registerId = "REG-01";

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "cashier_name", nullable = false, length = 100)
    private String cashierName;

    @Column(name = "opened_at", nullable = false)
    @Builder.Default
    private LocalDateTime openedAt = LocalDateTime.now();

    @Column(name = "opening_float", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal openingFloat = BigDecimal.ZERO;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ShiftStatus status = ShiftStatus.OPEN;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closing_user_id")
    private Long closingUserId;

    @Column(name = "closing_user_name", length = 100)
    private String closingUserName;

    @Column(name = "counted_cash", precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(name = "expected_cash", precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal variance;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cash_sales", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cashSales = BigDecimal.ZERO;

    @Column(name = "gift_card_cash_sales", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal giftCardCashSales = BigDecimal.ZERO;

    @Column(name = "upi_sales", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal upiSales = BigDecimal.ZERO;

    @Column(name = "card_sales", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cardSales = BigDecimal.ZERO;

    @Column(name = "cash_refunds", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cashRefunds = BigDecimal.ZERO;

    @Column(name = "upi_refunds", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal upiRefunds = BigDecimal.ZERO;

    @Column(name = "card_refunds", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cardRefunds = BigDecimal.ZERO;

    @Column(name = "cash_in_total", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cashInTotal = BigDecimal.ZERO;

    @Column(name = "cash_out_total", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cashOutTotal = BigDecimal.ZERO;

    @Column(name = "transfers_out_total", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal transfersOutTotal = BigDecimal.ZERO;

    @Column(name = "transfers_in_total", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal transfersInTotal = BigDecimal.ZERO;

    @Column(name = "previous_shift_id")
    private Long previousShiftId;

    @Column(name = "carry_forward_ref", length = 150)
    private String carryForwardRef;

    @Column(name = "is_corrected")
    @Builder.Default
    private Boolean isCorrected = false;

    @Column(name = "corrected_counted_cash", precision = 12, scale = 2)
    private BigDecimal correctedCountedCash;

    @Column(name = "corrected_variance", precision = 12, scale = 2)
    private BigDecimal correctedVariance;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    @Column(name = "corrected_by_user_id")
    private Long correctedByUserId;

    @Column(name = "corrected_by_user_name", length = 100)
    private String correctedByUserName;

    @Column(name = "corrected_at")
    private LocalDateTime correctedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ShiftStatus {
        OPEN, CLOSED
    }

}
