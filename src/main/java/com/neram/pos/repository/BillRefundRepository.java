package com.neram.pos.repository;

import com.neram.pos.entity.BillRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRefundRepository extends JpaRepository<BillRefund, Long> {
    List<BillRefund> findByBillId(Long billId);
    List<BillRefund> findByBillIdAndStatus(Long billId, BillRefund.RefundStatus status);
    Optional<BillRefund> findByIdempotencyKey(String idempotencyKey);
    List<BillRefund> findByShiftId(Long shiftId);
    List<BillRefund> findByRefundedAtBetween(LocalDateTime start, LocalDateTime end);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(r.amount), 0) FROM BillRefund r WHERE r.billId = :billId AND r.status = :status")
    java.math.BigDecimal sumRefundsByBillIdAndStatus(@org.springframework.data.repository.query.Param("billId") Long billId, @org.springframework.data.repository.query.Param("status") BillRefund.RefundStatus status);

    default java.math.BigDecimal sumSuccessfulRefundsByBillId(Long billId) {
        return sumRefundsByBillIdAndStatus(billId, BillRefund.RefundStatus.SUCCESS);
    }
}
