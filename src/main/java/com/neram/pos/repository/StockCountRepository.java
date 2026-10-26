package com.neram.pos.repository;

import com.neram.pos.entity.StockCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, Long> {
    List<StockCount> findTop50ByOrderByCreatedAtDesc();
    boolean existsByStatus(String status);
}
