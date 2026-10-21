package com.neram.pos.repository;

import com.neram.pos.entity.CashierShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashierShiftRepository extends JpaRepository<CashierShift, Long> {
    Optional<CashierShift> findByRegisterIdAndStatus(String registerId, CashierShift.ShiftStatus status);
    Optional<CashierShift> findByCashierIdAndStatus(Long cashierId, CashierShift.ShiftStatus status);
    Optional<CashierShift> findFirstByStatusOrderByOpenedAtDesc(CashierShift.ShiftStatus status);
    List<CashierShift> findAllByStatusOrderByOpenedAtDesc(CashierShift.ShiftStatus status);
    List<CashierShift> findByOpenedAtBetweenOrderByOpenedAtDesc(LocalDateTime start, LocalDateTime end);
    List<CashierShift> findAllByOrderByOpenedAtDesc();
}
