package com.neram.pos.repository;

import com.neram.pos.entity.GiftCardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GiftCardTransactionRepository extends JpaRepository<GiftCardTransaction, Long> {
    List<GiftCardTransaction> findByGiftCardIdOrderByCreatedAtDesc(Long giftCardId);
    List<GiftCardTransaction> findByBillId(Long billId);
}
