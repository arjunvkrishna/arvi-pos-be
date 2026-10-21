package com.neram.pos.repository;

import com.neram.pos.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Row-locked lookup so two concurrent refreshes of the same token cannot both rotate it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    List<RefreshToken> findByUserIdAndRevokedFalseOrderByCreatedAtAsc(Long userId);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true, t.updatedAt = :now WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiryDate < :cutoff OR (t.revoked = true AND t.updatedAt < :cutoff)")
    int deleteStale(@Param("cutoff") LocalDateTime cutoff);
}
