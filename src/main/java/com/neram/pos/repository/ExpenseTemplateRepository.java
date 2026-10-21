package com.neram.pos.repository;

import com.neram.pos.entity.ExpenseTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseTemplateRepository extends JpaRepository<ExpenseTemplate, Long> {
    List<ExpenseTemplate> findAllByOrderByDueDayAsc();
    List<ExpenseTemplate> findByIsActiveTrueOrderByDueDayAsc();
    boolean existsByTitleIgnoreCase(String title);
}
