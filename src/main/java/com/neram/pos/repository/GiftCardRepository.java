package com.neram.pos.repository;

import com.neram.pos.entity.GiftCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {
    Optional<GiftCard> findByCode(String code);

    @Modifying
    @Query("UPDATE GiftCard g SET g.currentBalance = g.currentBalance - :amount, " +
           "g.status = CASE WHEN (g.currentBalance - :amount) = 0 THEN com.neram.pos.entity.GiftCard.Status.EXHAUSTED ELSE g.status END " +
           "WHERE g.id = :id AND g.currentBalance >= :amount AND g.status = :activeStatus")
    int deductBalanceSafely(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("activeStatus") GiftCard.Status activeStatus);

    @Modifying
    @Query("UPDATE GiftCard g SET g.currentBalance = g.currentBalance + :amount, " +
           "g.status = CASE WHEN g.status = com.neram.pos.entity.GiftCard.Status.EXHAUSTED THEN com.neram.pos.entity.GiftCard.Status.ACTIVE ELSE g.status END " +
           "WHERE g.id = :id AND g.status != com.neram.pos.entity.GiftCard.Status.VOIDED")
    int restoreBalanceSafely(@Param("id") Long id, @Param("amount") BigDecimal amount);

    Page<GiftCard> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COALESCE(SUM(g.currentBalance), 0) FROM GiftCard g WHERE g.status = com.neram.pos.entity.GiftCard.Status.ACTIVE")
    BigDecimal sumActiveBalances();
}
