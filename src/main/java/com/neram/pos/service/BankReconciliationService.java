package com.neram.pos.service;

import com.neram.pos.dto.BankReconciliationDTO.*;
import com.neram.pos.entity.BankStatement;
import com.neram.pos.entity.BankStatementRow;
import com.neram.pos.entity.CafeExpense;
import com.neram.pos.repository.BankStatementRepository;
import com.neram.pos.repository.BankStatementRowRepository;
import com.neram.pos.repository.CafeExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankReconciliationService {

    private final BankStatementRepository statementRepository;
    private final BankStatementRowRepository rowRepository;
    private final CafeExpenseRepository expenseRepository;

    public List<StatementSummary> getAllStatements() {
        return statementRepository.findAllByOrderByImportDateDesc().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public List<StatementRow> getStatementRows(Long statementId, String status) {
        List<BankStatementRow> rows = rowRepository.findByStatementIdOrderByTransactionDateAsc(statementId);
        if (status != null && !status.isBlank()) {
            rows = rows.stream().filter(r -> r.getStatus().equalsIgnoreCase(status)).collect(Collectors.toList());
        }

        // Fetch matched expenses if needed
        Map<Long, CafeExpense> expenseMap = new HashMap<>();
        List<Long> expenseIds = rows.stream()
                .map(BankStatementRow::getMatchedExpenseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!expenseIds.isEmpty()) {
            expenseRepository.findAllById(expenseIds).forEach(e -> expenseMap.put(e.getId(), e));
        }

        return rows.stream().map(r -> {
            StatementRow dto = toRowDTO(r);
            if (r.getMatchedExpenseId() != null && expenseMap.containsKey(r.getMatchedExpenseId())) {
                CafeExpense e = expenseMap.get(r.getMatchedExpenseId());
                dto.setMatchedExpenseDescription(e.getDescription());
                dto.setMatchedExpenseAmount(e.getAmount());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public ImportResult importCsv(String description, InputStream inputStream, Long userId) {
        if (description == null || description.isBlank()) {
            description = "Bank Statement - " + LocalDate.now();
        }

        List<String[]> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                lines.add(parseCsvLine(line));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read CSV: " + e.getMessage(), e);
        }

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }

        // Detect column positions
        int dateCol = -1, descCol = -1, amountCol = -1, balanceCol = -1, refCol = -1;
        int startIndex = 0;

        String[] firstRow = lines.get(0);
        for (int i = 0; i < firstRow.length; i++) {
            String h = firstRow[i].toLowerCase().trim().replace("\"", "");
            if (h.contains("date") && dateCol == -1) dateCol = i;
            else if ((h.contains("desc") || h.contains("narr") || h.contains("particular")) && descCol == -1) descCol = i;
            else if ((h.contains("amount") || h.contains("withdrawal") || h.contains("debit")) && amountCol == -1) amountCol = i;
            else if (h.contains("balance") && balanceCol == -1) balanceCol = i;
            else if ((h.contains("ref") || h.contains("chq") || h.contains("cheque") || h.contains("utr")) && refCol == -1) refCol = i;
        }

        // If headers found, skip header line
        if (dateCol != -1 && amountCol != -1) {
            startIndex = 1;
            if (descCol == -1) descCol = 1;
        } else {
            // Default columns: 0: date, 1: desc, 2: amount, 3: balance, 4: ref
            dateCol = 0;
            descCol = 1;
            amountCol = 2;
            balanceCol = firstRow.length > 3 ? 3 : -1;
            refCol = firstRow.length > 4 ? 4 : -1;
        }

        BankStatement statement = BankStatement.builder()
                .description(description)
                .importDate(Instant.now())
                .rowCount(0)
                .importedBy(userId)
                .build();
        statement = statementRepository.save(statement);

        int total = 0;
        int imported = 0;
        int skipped = 0;
        Map<String, Integer> occurrenceCounter = new HashMap<>();
        List<BankStatementRow> savedRows = new ArrayList<>();

        for (int i = startIndex; i < lines.size(); i++) {
            String[] cols = lines.get(i);
            if (cols.length <= Math.max(dateCol, amountCol)) continue;
            total++;

            LocalDate txDate = parseDate(cols[dateCol].trim());
            if (txDate == null) continue;

            String desc = (descCol != -1 && cols.length > descCol) ? cols[descCol].trim() : "";
            BigDecimal amount = parseAmount(cols[amountCol].trim());
            if (amount == null) continue;

            BigDecimal balance = (balanceCol != -1 && cols.length > balanceCol) ? parseAmount(cols[balanceCol].trim()) : null;
            String refNo = (refCol != -1 && cols.length > refCol) ? cols[refCol].trim() : null;

            String normDesc = desc.trim().replaceAll("\\s+", " ").toUpperCase();
            String occKey = txDate + "|" + amount.toPlainString() + "|" + normDesc;
            int occ = occurrenceCounter.getOrDefault(occKey, 0) + 1;
            occurrenceCounter.put(occKey, occ);

            String hash = calculateHash(txDate, amount, normDesc, refNo, balance, occ);

            if (rowRepository.existsByImportRowHash(hash)) {
                skipped++;
                continue;
            }

            BankStatementRow row = BankStatementRow.builder()
                    .statement(statement)
                    .transactionDate(txDate)
                    .description(desc)
                    .amount(amount)
                    .balance(balance)
                    .refNo(refNo)
                    .status("UNMATCHED")
                    .importRowHash(hash)
                    .build();

            savedRows.add(rowRepository.save(row));
            imported++;
        }

        statement.setRowCount(imported);
        statementRepository.save(statement);

        // Run auto-match on the newly imported rows
        int autoMatched = autoMatchRows(savedRows);

        return ImportResult.builder()
                .statementId(statement.getId())
                .totalRows(total)
                .importedRows(imported)
                .skippedDuplicates(skipped)
                .autoMatchedCount(autoMatched)
                .build();
    }

    @Transactional
    public int autoMatchAllUnmatched() {
        List<BankStatementRow> unmatched = rowRepository.findByStatusOrderByTransactionDateDesc("UNMATCHED");
        return autoMatchRows(unmatched);
    }

    private int autoMatchRows(List<BankStatementRow> rows) {
        if (rows.isEmpty()) return 0;

        LocalDate minDate = rows.stream().map(BankStatementRow::getTransactionDate).min(LocalDate::compareTo).orElse(LocalDate.now()).minusDays(5);
        LocalDate maxDate = rows.stream().map(BankStatementRow::getTransactionDate).max(LocalDate::compareTo).orElse(LocalDate.now()).plusDays(5);

        List<CafeExpense> expenses = expenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(minDate, maxDate);
        Set<Long> alreadyMatchedExpenseIds = rowRepository.findAll().stream()
                .map(BankStatementRow::getMatchedExpenseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<CafeExpense> availableExpenses = expenses.stream()
                .filter(e -> !alreadyMatchedExpenseIds.contains(e.getId()))
                .collect(Collectors.toList());

        int matchedCount = 0;
        for (BankStatementRow row : rows) {
            if (!"UNMATCHED".equals(row.getStatus())) continue;
            BigDecimal rowAbs = row.getAmount().abs();

            List<CafeExpense> candidates = availableExpenses.stream()
                    .filter(e -> e.getAmount() != null && e.getAmount().compareTo(rowAbs) == 0)
                    .filter(e -> !e.getExpenseDate().isBefore(row.getTransactionDate().minusDays(5)) &&
                                 !e.getExpenseDate().isAfter(row.getTransactionDate().plusDays(5)))
                    .collect(Collectors.toList());

            if (candidates.size() == 1) {
                CafeExpense matched = candidates.get(0);
                row.setStatus("MATCHED");
                row.setMatchedExpenseId(matched.getId());
                row.setMatchedAt(Instant.now());
                rowRepository.save(row);

                availableExpenses.remove(matched);
                matchedCount++;
            }
        }
        return matchedCount;
    }

    @Transactional
    public StatementRow manualMatch(Long rowId, Long expenseId) {
        BankStatementRow row = rowRepository.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Statement row not found: " + rowId));
        CafeExpense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + expenseId));

        row.setStatus("MATCHED");
        row.setMatchedExpenseId(expense.getId());
        row.setMatchedAt(Instant.now());
        row = rowRepository.save(row);

        StatementRow dto = toRowDTO(row);
        dto.setMatchedExpenseDescription(expense.getDescription());
        dto.setMatchedExpenseAmount(expense.getAmount());
        return dto;
    }

    @Transactional
    public StatementRow unmatch(Long rowId) {
        BankStatementRow row = rowRepository.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Statement row not found: " + rowId));

        row.setStatus("UNMATCHED");
        row.setMatchedExpenseId(null);
        row.setMatchedAt(null);
        return toRowDTO(rowRepository.save(row));
    }

    @Transactional
    public StatementRow ignoreRow(Long rowId) {
        BankStatementRow row = rowRepository.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Statement row not found: " + rowId));

        row.setStatus("IGNORED");
        row.setMatchedExpenseId(null);
        row.setMatchedAt(null);
        return toRowDTO(rowRepository.save(row));
    }

    private String calculateHash(LocalDate date, BigDecimal amount, String normDesc, String refNo, BigDecimal balance, int occurrence) {
        String raw = date.toString() + "|" + amount.setScale(2).toPlainString() + "|" + normDesc + "|"
                + (refNo != null ? refNo.trim().toUpperCase() : "") + "|"
                + (balance != null ? balance.setScale(2).toPlainString() : "") + "|"
                + occurrence;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replace("\"", "").trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy")
        );
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(cleaned, fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replace("\"", "").replace("₹", "").replace(",", "").trim();
        boolean isNegative = false;
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            isNegative = true;
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        } else if (cleaned.startsWith("-")) {
            isNegative = true;
            cleaned = cleaned.substring(1).trim();
        }
        try {
            BigDecimal val = new BigDecimal(cleaned);
            return isNegative ? val.negate() : val;
        } catch (Exception e) {
            return null;
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    private StatementSummary toSummary(BankStatement s) {
        List<BankStatementRow> rows = rowRepository.findByStatementIdOrderByTransactionDateAsc(s.getId());
        long matched = rows.stream().filter(r -> "MATCHED".equals(r.getStatus())).count();
        long unmatched = rows.stream().filter(r -> "UNMATCHED".equals(r.getStatus())).count();

        return StatementSummary.builder()
                .id(s.getId())
                .description(s.getDescription())
                .importDate(s.getImportDate())
                .rowCount(s.getRowCount())
                .importedBy(s.getImportedBy())
                .matchedCount(matched)
                .unmatchedCount(unmatched)
                .build();
    }

    private StatementRow toRowDTO(BankStatementRow r) {
        return StatementRow.builder()
                .id(r.getId())
                .statementId(r.getStatement() != null ? r.getStatement().getId() : null)
                .transactionDate(r.getTransactionDate())
                .description(r.getDescription())
                .amount(r.getAmount())
                .balance(r.getBalance())
                .refNo(r.getRefNo())
                .status(r.getStatus())
                .matchedExpenseId(r.getMatchedExpenseId())
                .matchedAt(r.getMatchedAt())
                .build();
    }
}
