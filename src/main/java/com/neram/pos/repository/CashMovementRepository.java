package com.neram.pos.repository;

import com.neram.pos.entity.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    List<CashMovement> findByShiftIdOrderByCreatedAtAsc(Long shiftId);
    List<CashMovement> findByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
