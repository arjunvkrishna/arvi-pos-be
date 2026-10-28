package com.neram.pos.repository;

import com.neram.pos.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    long countByCouponIdAndCustomerId(Long couponId, Long customerId);

    Optional<CouponUsage> findByBillId(Long billId);

    void deleteByBillId(Long billId);

    List<CouponUsage> findByCouponId(Long couponId);
}
