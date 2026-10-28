package com.neram.pos.service;

import com.neram.pos.dto.SupplierAnalyticsDTO;
import com.neram.pos.entity.Supplier;
import com.neram.pos.entity.SupplierInvoice;
import com.neram.pos.repository.SupplierInvoiceRepository;
import com.neram.pos.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierAnalyticsService {

    private final SupplierRepository supplierRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;

    @Transactional(readOnly = true)
    public SupplierAnalyticsDTO getSupplierAnalytics(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found with id: " + supplierId));

        List<SupplierInvoice> invoices = supplierInvoiceRepository.findBySupplierId(supplierId);
        return buildAnalytics(supplier, invoices);
    }

    @Transactional(readOnly = true)
    public List<SupplierAnalyticsDTO> getAllSuppliersAnalytics() {
        List<Supplier> suppliers = supplierRepository.findAll();
        List<SupplierAnalyticsDTO> result = new ArrayList<>();
        for (Supplier s : suppliers) {
            List<SupplierInvoice> invoices = supplierInvoiceRepository.findBySupplierId(s.getId());
            result.add(buildAnalytics(s, invoices));
        }
        return result;
    }

    private SupplierAnalyticsDTO buildAnalytics(Supplier supplier, List<SupplierInvoice> invoices) {
        BigDecimal totalSpend = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstanding = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        int onTimeCount = 0;
        int trackedDeliveryCount = 0;
        long totalLeadDays = 0;
        int leadDaysCount = 0;

        List<SupplierAnalyticsDTO.InvoiceSummaryDTO> recent = new ArrayList<>();

        for (SupplierInvoice inv : invoices) {
            BigDecimal tot = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal out = inv.getOutstandingAmount() != null ? inv.getOutstandingAmount() : BigDecimal.ZERO;

            totalSpend = totalSpend.add(tot);
            totalPaid = totalPaid.add(paid);
            outstanding = outstanding.add(out);

            Boolean onTime = null;
            if (inv.getDeliveryDate() != null) {
                // On time only means something against a promised date; the payment due date is not a delivery promise
                if (inv.getExpectedDeliveryDate() != null) {
                    trackedDeliveryCount++;
                    onTime = !inv.getDeliveryDate().isAfter(inv.getExpectedDeliveryDate());
                    if (onTime) {
                        onTimeCount++;
                    }
                }

                if (inv.getInvoiceDate() != null) {
                    long days = ChronoUnit.DAYS.between(inv.getInvoiceDate(), inv.getDeliveryDate());
                    if (days >= 0) {
                        totalLeadDays += days;
                        leadDaysCount++;
                    }
                }
            }

            recent.add(SupplierAnalyticsDTO.InvoiceSummaryDTO.builder()
                    .id(inv.getId())
                    .invoiceNumber(inv.getInvoiceNumber())
                    .invoiceDate(inv.getInvoiceDate() != null ? inv.getInvoiceDate().toString() : null)
                    .totalAmount(tot)
                    .status(inv.getStatus() != null ? inv.getStatus().name() : null)
                    .expectedDeliveryDate(inv.getExpectedDeliveryDate() != null ? inv.getExpectedDeliveryDate().toString() : null)
                    .deliveryDate(inv.getDeliveryDate() != null ? inv.getDeliveryDate().toString() : null)
                    .isOnTime(onTime)
                    .build());
        }

        // No recorded delivery dates means no figure (null), never an invented one
        Double onTimeRate = (trackedDeliveryCount > 0) ? ((double) onTimeCount / trackedDeliveryCount) * 100.0 : null;
        Double avgLeadTime = (leadDaysCount > 0) ? ((double) totalLeadDays / leadDaysCount) : null;

        // Fulfillment rate: invoices not overdue or disputed
        Double fulfillmentRate = invoices.isEmpty() ? null :
                Math.min(100.0, (1.0 - ((double) invoices.stream().filter(i -> i.getStatus() == SupplierInvoice.InvoiceStatus.OVERDUE).count() / invoices.size())) * 100.0);

        return SupplierAnalyticsDTO.builder()
                .supplierId(supplier.getId())
                .supplierName(supplier.getName())
                .totalInvoices(invoices.size())
                .totalSpend(totalSpend)
                .totalPaid(totalPaid)
                .outstandingBalance(outstanding)
                .onTimeDeliveryRatePercent(round1(onTimeRate))
                .averageLeadTimeDays(round1(avgLeadTime))
                .fulfillmentRatePercent(round1(fulfillmentRate))
                .recentInvoices(recent.stream().limit(10).collect(Collectors.toList()))
                .build();
    }

    private static Double round1(Double v) {
        return v == null ? null : Math.round(v * 10.0) / 10.0;
    }
}
