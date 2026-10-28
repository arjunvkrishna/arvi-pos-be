package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bank_statements")
public class BankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String description;

    @Column(name = "import_date", nullable = false)
    @Builder.Default
    private Instant importDate = Instant.now();

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "imported_by")
    private Long importedBy;

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BankStatementRow> rows = new ArrayList<>();
}
