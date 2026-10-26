package com.neram.pos.repository;

import com.neram.pos.entity.SalaryAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SalaryAdvanceRepository extends JpaRepository<SalaryAdvance, Long> {
    List<SalaryAdvance> findBySalaryIdOrderByIdAsc(Long salaryId);
    List<SalaryAdvance> findBySalaryIdIn(Collection<Long> salaryIds);
}
