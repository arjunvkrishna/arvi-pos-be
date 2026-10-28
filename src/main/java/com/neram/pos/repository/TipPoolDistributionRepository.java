package com.neram.pos.repository;

import com.neram.pos.entity.TipPoolDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TipPoolDistributionRepository extends JpaRepository<TipPoolDistribution, Long> {

    /** The tip pool of one branch for a day; a null outlet means the cafe-wide pool (an owner with no branch picked). */
    @Query("SELECT d FROM TipPoolDistribution d LEFT JOIN FETCH d.shares s LEFT JOIN FETCH s.employee "
            + "WHERE d.distributionDate = :date AND ((:outletId IS NULL AND d.outletId IS NULL) OR d.outletId = :outletId)")
    Optional<TipPoolDistribution> findByDistributionDateWithShares(@Param("date") LocalDate date, @Param("outletId") Long outletId);

    @Query("SELECT d FROM TipPoolDistribution d WHERE d.distributionDate = :date "
            + "AND ((:outletId IS NULL AND d.outletId IS NULL) OR d.outletId = :outletId)")
    Optional<TipPoolDistribution> findByDistributionDate(@Param("date") LocalDate date, @Param("outletId") Long outletId);
}
