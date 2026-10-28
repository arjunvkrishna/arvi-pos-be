package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "supplier_invoices")
@org.hibernate.annotations.Filter(name = "outletScope", condition = "(outlet_id = :outletId OR outlet_id IS NULL)")
public class SupplierInvoice implements com.neram.pos.access.OutletScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;

    @Column(name = "gst_rate")
    private Double gstRate = 0.0;

    @Column(name = "gst_amount", precision = 12, scale = 2)
    private java.math.BigDecimal gstAmount = java.math.BigDecimal.ZERO;

    @Column(name = "other_charges", precision = 12, scale = 2)
    private java.math.BigDecimal otherCharges = java.math.BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal discount = java.math.BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal paidAmount = java.math.BigDecimal.ZERO;

    @Column(name = "outstanding_amount", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal outstandingAmount = java.math.BigDecimal.ZERO;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "items_received", columnDefinition = "TEXT")
    private String itemsReceived;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum InvoiceStatus { UNPAID, PARTIAL, PAID, OVERDUE }

    @Column(name = "outlet_id")
    private Long outletId;
}
