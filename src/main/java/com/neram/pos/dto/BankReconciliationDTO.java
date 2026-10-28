package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class BankReconciliationDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatementSummary {
        private Long id;
        private String description;
        private Instant importDate;
        private Integer rowCount;
        private Long importedBy;
        private long matchedCount;
        private long unmatchedCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatementRow {
        private Long id;
        private Long statementId;
        private LocalDate transactionDate;
        private String description;
        private BigDecimal amount;
        private BigDecimal balance;
        private String refNo;
        private String status; // UNMATCHED, MATCHED, IGNORED
        private Long matchedExpenseId;
        private Instant matchedAt;
        private String matchedExpenseDescription;
        private BigDecimal matchedExpenseAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchRequest {
        private Long expenseId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportResult {
        private Long statementId;
        private int totalRows;
        private int importedRows;
        private int skippedDuplicates;
        private int autoMatchedCount;
    }
}
