package com.neram.pos.repository;

import com.neram.pos.entity.LoyaltyLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoyaltyLedgerRepository extends JpaRepository<LoyaltyLedger, Long> {
    List<LoyaltyLedger> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<LoyaltyLedger> findByBillId(Long billId);
}
