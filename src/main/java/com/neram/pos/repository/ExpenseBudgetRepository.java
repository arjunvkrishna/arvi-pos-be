package com.neram.pos.repository;

import com.neram.pos.entity.ExpenseBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpenseBudgetRepository extends JpaRepository<ExpenseBudget, Long> {
    Optional<ExpenseBudget> findByCategory(String category);
}
