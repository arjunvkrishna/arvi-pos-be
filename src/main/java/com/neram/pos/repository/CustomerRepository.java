package com.neram.pos.repository;

import com.neram.pos.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

    Optional<Customer> findByUnsubscribeToken(String token);

    @Query("SELECT c FROM Customer c WHERE LOWER(c.phone) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Customer> search(@Param("q") String q);

    Page<Customer> findByIsActiveTrueOrderByNameAsc(Pageable pageable);

    List<Customer> findByMarketingConsentTrueAndIsActiveTrue();

    @Query("SELECT c.id FROM Customer c WHERE c.pointsBalance > 0 AND c.pointsExpireAt IS NOT NULL AND c.pointsExpireAt < :today")
    List<Long> findIdsDueForExpiry(@Param("today") java.time.LocalDate today);

    @Query("SELECT c FROM Customer c WHERE c.marketingConsent = true AND c.isActive = true AND c.pointsBalance > 0")
    List<Customer> findLoyaltyAudience();

    @Query("SELECT c FROM Customer c WHERE c.marketingConsent = true AND c.isActive = true AND (NOT EXISTS (SELECT b FROM Bill b WHERE b.customerId = c.id) OR (SELECT MAX(b.createdAt) FROM Bill b WHERE b.customerId = c.id) < :cutoff)")
    List<Customer> findInactiveAudience(@Param("cutoff") java.time.LocalDateTime cutoff);
}
