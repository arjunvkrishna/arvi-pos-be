package com.neram.pos.repository;

import com.neram.pos.entity.Salary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryRepository extends JpaRepository<Salary, Long> {
    List<Salary> findByEmployeeId(Long employeeId);
    List<Salary> findByMonthAndYear(Integer month, Integer year);
    Optional<Salary> findByEmployeeIdAndMonthAndYear(Long employeeId, Integer month, Integer year);
    List<Salary> findByPaidTrueAndPaidDateBetween(java.time.LocalDate start, java.time.LocalDate end);
}
