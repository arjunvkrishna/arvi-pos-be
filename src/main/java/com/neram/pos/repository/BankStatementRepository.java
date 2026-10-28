package com.neram.pos.repository;

import com.neram.pos.entity.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {
    List<BankStatement> findAllByOrderByImportDateDesc();
}
