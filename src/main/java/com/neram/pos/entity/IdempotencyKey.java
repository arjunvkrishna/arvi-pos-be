package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Remembers which bill a saved order produced, so sending the same order twice (a lost reply, a retry after reconnecting) never creates it twice. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    @Id
    @Column(name = "idem_key", length = 80)
    private String key;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
