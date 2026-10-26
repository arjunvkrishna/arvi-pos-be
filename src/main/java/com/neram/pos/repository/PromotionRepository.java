package com.neram.pos.repository;

import com.neram.pos.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByIsActiveTrue();

    @Query("SELECT p FROM Promotion p WHERE p.isActive = true AND p.startDateTime <= :now AND p.endDateTime >= :now ORDER BY p.createdAt DESC")
    List<Promotion> findActivePromotionsAt(@Param("now") LocalDateTime now);

    List<Promotion> findAllByOrderByCreatedAtDesc();

    org.springframework.data.domain.Page<Promotion> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);
}
