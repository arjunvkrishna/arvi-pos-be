package com.neram.pos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bank_statement_rows")
public class BankStatementRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id", nullable = false)
    @JsonIgnore
    private BankStatement statement;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(precision = 10, scale = 2)
    private BigDecimal balance;

    @Column(name = "ref_no", length = 100)
    private String refNo;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "UNMATCHED"; // UNMATCHED, MATCHED, IGNORED

    @Column(name = "matched_expense_id")
    private Long matchedExpenseId;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "import_row_hash", nullable = false, unique = true, length = 64)
    private String importRowHash;
}
