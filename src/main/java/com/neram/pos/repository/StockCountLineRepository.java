package com.neram.pos.repository;

import com.neram.pos.entity.StockCountLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockCountLineRepository extends JpaRepository<StockCountLine, Long> {
    List<StockCountLine> findByCountIdOrderByItemNameAsc(Long countId);
}
