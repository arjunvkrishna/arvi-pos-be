package com.neram.pos.repository;

import com.neram.pos.entity.CafeExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CafeExpenseRepository extends JpaRepository<CafeExpense, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<CafeExpense> {
    List<CafeExpense> findByType(CafeExpense.ExpenseType type);
    List<CafeExpense> findByMonthAndYear(Integer month, Integer year);
    List<CafeExpense> findByMonthAndYearOrderByExpenseDateDesc(Integer month, Integer year);
    List<CafeExpense> findByTemplateIdAndMonthAndYear(Long templateId, Integer month, Integer year);
    List<CafeExpense> findByStatus(String status);
    List<CafeExpense> findByIsRecurringTrue();
    List<CafeExpense> findByExpenseDateBetween(LocalDate start, LocalDate end);
    List<CafeExpense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate start, LocalDate end);
    org.springframework.data.domain.Page<CafeExpense> findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(LocalDate start, LocalDate end, org.springframework.data.domain.Pageable pageable);
    List<CafeExpense> findAllByOrderByExpenseDateDesc();
}
