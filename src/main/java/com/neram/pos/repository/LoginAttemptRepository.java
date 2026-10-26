package com.neram.pos.repository;

import com.neram.pos.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    Optional<LoginAttempt> findByScopeAndAttemptKey(String scope, String attemptKey);

    List<LoginAttempt> findByScopeAndAttemptKeyIn(String scope, Collection<String> attemptKeys);

    @Modifying
    @Transactional
    int deleteByScopeAndAttemptKey(String scope, String attemptKey);

    /** Housekeeping: rows that are not locked any more and have seen no failure for a long time. */
    @Modifying
    @Transactional
    @Query("DELETE FROM LoginAttempt a WHERE (a.lockedUntil IS NULL OR a.lockedUntil < :now) AND a.lastFailedAt < :staleBefore")
    int deleteStale(@Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);
}
