package com.neram.pos.repository;

import com.neram.pos.entity.SupplierInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {
    List<SupplierInvoice> findBySupplierId(Long supplierId);
    org.springframework.data.domain.Page<SupplierInvoice> findBySupplierId(Long supplierId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<SupplierInvoice> findByStatus(SupplierInvoice.InvoiceStatus status, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<SupplierInvoice> findBySupplierIdAndStatus(Long supplierId, SupplierInvoice.InvoiceStatus status, org.springframework.data.domain.Pageable pageable);
    List<SupplierInvoice> findByStatus(SupplierInvoice.InvoiceStatus status);
    List<SupplierInvoice> findByInvoiceDateBetweenOrderByInvoiceDateDesc(LocalDate start, LocalDate end);
    org.springframework.data.domain.Page<SupplierInvoice> findByInvoiceDateBetweenOrderByInvoiceDateDescIdDesc(LocalDate start, LocalDate end, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT si FROM SupplierInvoice si WHERE si.status IN ('UNPAID','PARTIAL','OVERDUE')")
    List<SupplierInvoice> findAllOutstanding();

    @Query("SELECT si FROM SupplierInvoice si WHERE si.status IN ('UNPAID','PARTIAL') AND si.dueDate < :today")
    List<SupplierInvoice> findOverdue(@Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(si.outstandingAmount), 0) FROM SupplierInvoice si WHERE si.supplierId = :supplierId AND si.status IN ('UNPAID','PARTIAL','OVERDUE')")
    java.math.BigDecimal totalOutstandingBySupplier(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(si.outstandingAmount), 0) FROM SupplierInvoice si WHERE si.status IN ('UNPAID','PARTIAL','OVERDUE')")
    java.math.BigDecimal totalOutstandingAll();
}
