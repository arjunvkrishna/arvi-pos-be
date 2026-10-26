package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(com.neram.pos.config.OrderChangeListener.class)
@Table(name = "bills")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class Bill implements com.neram.pos.tenancy.TenantScoped , com.neram.pos.access.OutletScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_number", nullable = false, unique = true)
    private String billNumber;

    @Column(name = "table_number")
    private String tableNumber;

    @Column(name = "customer_name")
    private String customerName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_percent")
    private Double discountPercent = 0.0;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_percent")
    private Double taxPercent = 0.0;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "gst_enabled")
    private Boolean gstEnabled;

    @Column(name = "gstin")
    private String gstin;

    @Column(name = "cgst_percent")
    private Double cgstPercent = 0.0;

    @Column(name = "sgst_percent")
    private Double sgstPercent = 0.0;

    @Column(name = "cgst_amount", precision = 12, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", precision = 12, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "round_off", precision = 12, scale = 2)
    private BigDecimal roundOff = BigDecimal.ZERO;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "promotion_name")
    private String promotionName;

    @Column(name = "promotion_percentage")
    private Double promotionPercentage;

    @Column(name = "promotion_discount_amount", precision = 12, scale = 2)
    private BigDecimal promotionDiscountAmount = BigDecimal.ZERO;

    @Column(name = "parcel_charge", precision = 12, scale = 2)
    private BigDecimal parcelCharge = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    /** Tip given when the bill was paid. Not part of the net amount, GST, sales figures or the cash drawer. */
    @Column(name = "tip_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal tipAmount = BigDecimal.ZERO;

    /** Text such as "CASH ₹200.00 + UPI ₹100.00" when the bill was paid in more than one way; null otherwise. */
    @Column(name = "payment_summary", length = 150)
    private String paymentSummary;

    @Column(name = "payment_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode = PaymentMode.CASH;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PAID;

    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "printed_at")
    private LocalDateTime printedAt;

    @Column(name = "printed_by")
    private Long printedBy;

    @Column(name = "kot_printed_at")
    private LocalDateTime kotPrintedAt;

    @Column(name = "kot_number")
    private String kotNumber;

    @Column(name = "kot_update_count", nullable = false)
    private Integer kotUpdateCount = 0;

    @Column(name = "kot_updated_at")
    private LocalDateTime kotUpdatedAt;

    @Column(name = "kot_last_changes", columnDefinition = "TEXT")
    private String kotLastChanges;

    @Column(name = "kot_cancelled_at")
    private LocalDateTime kotCancelledAt;

    @Column(name = "order_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderType orderType = OrderType.TABLE;

    @Column(name = "outdoor_token")
    private String outdoorToken;

    @Column(name = "takeaway_token")
    private String takeawayToken;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_by_name")
    private String voidedByName;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_by_name")
    private String deletedByName;

    @Column(name = "delete_reason", columnDefinition = "TEXT")
    private String deleteReason;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BillItem> items = new ArrayList<>();

    public enum PaymentMode { CASH, UPI, CARD }
    public enum Status { DRAFT, ACTIVE, KOT_REQUESTED, KOT_PRINTED, BILL_REQUESTED, BILL_PRINTED, PAID, CANCELLED, REFUNDED, PARTIALLY_REFUNDED, VOIDED }
    public enum OrderType { TABLE, OUTDOOR, TAKEAWAY }
}
