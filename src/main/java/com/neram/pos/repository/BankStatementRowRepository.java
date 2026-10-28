package com.neram.pos.repository;

import com.neram.pos.entity.BankStatementRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankStatementRowRepository extends JpaRepository<BankStatementRow, Long> {

    List<BankStatementRow> findByStatementIdOrderByTransactionDateAsc(Long statementId);

    Optional<BankStatementRow> findByImportRowHash(String hash);

    boolean existsByImportRowHash(String hash);

    List<BankStatementRow> findByStatusOrderByTransactionDateDesc(String status);
}
