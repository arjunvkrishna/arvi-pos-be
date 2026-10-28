package com.neram.pos.repository;

import com.neram.pos.entity.ShiftSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ShiftScheduleRepository extends JpaRepository<ShiftSchedule, Long> {

    List<ShiftSchedule> findByEmployeeIdOrderByStartsAtAsc(Long employeeId);

    @Query("SELECT s FROM ShiftSchedule s JOIN FETCH s.employee WHERE s.startsAt >= :from AND s.startsAt <= :to ORDER BY s.startsAt ASC")
    List<ShiftSchedule> findSchedulesBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT s FROM ShiftSchedule s WHERE s.employee.id = :employeeId AND s.status <> 'CANCELLED' AND (:excludeId IS NULL OR s.id <> :excludeId) AND s.startsAt < :endsAt AND s.endsAt > :startsAt")
    List<ShiftSchedule> findOverlappingShifts(
            @Param("employeeId") Long employeeId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("excludeId") Long excludeId
    );
}
