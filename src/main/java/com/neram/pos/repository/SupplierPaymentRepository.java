package com.neram.pos.repository;

import com.neram.pos.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findByInvoiceId(Long invoiceId);
    List<SupplierPayment> findBySupplierId(Long supplierId);
    org.springframework.data.domain.Page<SupplierPayment> findBySupplierId(Long supplierId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT sp FROM SupplierPayment sp WHERE sp.supplierId = :supplierId ORDER BY sp.paymentDate DESC")
    List<SupplierPayment> findBySupplierIdOrderByDateDesc(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SupplierPayment sp WHERE sp.supplierId = :supplierId")
    java.math.BigDecimal totalPaidBySupplier(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SupplierPayment sp WHERE sp.paymentDate BETWEEN :from AND :to")
    java.math.BigDecimal totalPaidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SupplierPayment sp WHERE sp.invoiceId = :invoiceId")
    java.math.BigDecimal totalPaidForInvoice(@Param("invoiceId") Long invoiceId);

    List<SupplierPayment> findByPaymentDateBetweenOrderByPaymentDateDesc(LocalDate from, LocalDate to);
    org.springframework.data.domain.Page<SupplierPayment> findByPaymentDateBetweenOrderByPaymentDateDescIdDesc(LocalDate from, LocalDate to, org.springframework.data.domain.Pageable pageable);
}
