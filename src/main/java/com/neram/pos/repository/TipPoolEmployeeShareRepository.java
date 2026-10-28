package com.neram.pos.repository;

import com.neram.pos.entity.TipPoolEmployeeShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TipPoolEmployeeShareRepository extends JpaRepository<TipPoolEmployeeShare, Long> {

    List<TipPoolEmployeeShare> findByDistributionId(Long distributionId);

    List<TipPoolEmployeeShare> findByEmployeeIdOrderByPaidAsc(Long employeeId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(s.tipAmount), 0) FROM TipPoolEmployeeShare s WHERE s.paid = false")
    java.math.BigDecimal sumUnpaidTips();
}
