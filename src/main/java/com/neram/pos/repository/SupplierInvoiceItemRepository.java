package com.neram.pos.repository;

import com.neram.pos.entity.SupplierInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierInvoiceItemRepository extends JpaRepository<SupplierInvoiceItem, Long> {
    List<SupplierInvoiceItem> findByInvoiceId(Long invoiceId);
    void deleteByInvoiceId(Long invoiceId);
    List<SupplierInvoiceItem> findByInventoryItemIdIn(java.util.Collection<Long> inventoryItemIds);

    @org.springframework.data.jpa.repository.Query(
        "SELECT sii.productName, sii.unit, SUM(sii.quantity), SUM(sii.totalPrice) " +
        "FROM SupplierInvoiceItem sii, SupplierInvoice si " +
        "WHERE sii.invoiceId = si.id AND si.invoiceDate BETWEEN :start AND :end " +
        "GROUP BY sii.productName, sii.unit " +
        "ORDER BY SUM(sii.totalPrice) DESC"
    )
    List<Object[]> findPurchasedProductsBetween(@org.springframework.data.repository.query.Param("start") java.time.LocalDate start,
                                                @org.springframework.data.repository.query.Param("end") java.time.LocalDate end);
}
