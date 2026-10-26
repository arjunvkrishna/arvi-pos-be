package com.neram.pos.repository;

import com.neram.pos.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByDate(LocalDate date);
    org.springframework.data.domain.Page<Attendance> findByDate(LocalDate date, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Attendance> findByEmployeeId(Long employeeId, org.springframework.data.domain.Pageable pageable);
    List<Attendance> findByEmployeeId(Long employeeId);
    List<Attendance> findByEmployeeIdOrderByDateDesc(Long employeeId);
    List<Attendance> findByEmployeeIdOrderByDateDescIdDesc(Long employeeId);
    List<Attendance> findByEmployeeIdAndDateBetween(Long employeeId, LocalDate start, LocalDate end);
    List<Attendance> findByEmployeeIdAndDateBetweenOrderByDateDesc(Long employeeId, LocalDate start, LocalDate end);
    List<Attendance> findByEmployeeIdAndDateBetweenOrderByDateDescIdDesc(Long employeeId, LocalDate start, LocalDate end);
    List<Attendance> findByEmployeeIdAndDate(Long employeeId, LocalDate date);
    List<Attendance> findByEmployeeIdAndDateOrderByIdAsc(Long employeeId, LocalDate date);
    List<Attendance> findByEmployeeIdAndDateOrderByIdDesc(Long employeeId, LocalDate date);
    Optional<Attendance> findTopByEmployeeIdAndDateOrderByIdDesc(Long employeeId, LocalDate date);
}
