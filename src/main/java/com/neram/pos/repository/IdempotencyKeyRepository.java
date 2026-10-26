package com.neram.pos.repository;

import com.neram.pos.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
