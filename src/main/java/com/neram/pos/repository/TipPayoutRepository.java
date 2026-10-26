package com.neram.pos.repository;

import com.neram.pos.entity.TipPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TipPayoutRepository extends JpaRepository<TipPayout, Long> {
    List<TipPayout> findByPaidOnBetweenOrderByPaidOnDescIdDesc(LocalDate start, LocalDate end);
    List<TipPayout> findTop100ByOrderByPaidOnDescIdDesc();
}
