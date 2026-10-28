package com.neram.pos.repository;

import com.neram.pos.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE UPPER(c.code) = UPPER(:code)")
    Optional<Coupon> findByCodeForUpdate(@Param("code") String code);

    @Modifying
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount + 1 " +
           "WHERE c.id = :id AND c.isActive = true " +
           "AND c.validFrom <= :now " +
           "AND (c.validUntil IS NULL OR c.validUntil >= :now) " +
           "AND (c.usageLimit IS NULL OR c.usedCount < c.usageLimit)")
    int incrementUsedCountSafely(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE Coupon c SET c.usedCount = CASE WHEN c.usedCount > 0 THEN c.usedCount - 1 ELSE 0 END WHERE c.id = :id")
    int decrementUsedCountSafely(@Param("id") Long id);

    List<Coupon> findAllByOrderByCreatedAtDesc();
}
