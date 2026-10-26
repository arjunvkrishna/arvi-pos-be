package com.neram.pos.repository;

import com.neram.pos.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {
    List<BillPayment> findByBillIdOrderByIdAsc(Long billId);
    List<BillPayment> findByBillIdIn(Collection<Long> billIds);
    void deleteByBillId(Long billId);
}
