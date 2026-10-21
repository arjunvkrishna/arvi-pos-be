package com.neram.pos.repository;

import com.neram.pos.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
    Optional<Bill> findByBillNumber(String billNumber);

    List<Bill> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    List<Bill> findAllByOrderByCreatedAtDesc();

    @Query("SELECT b FROM Bill b WHERE LOWER(b.billNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(b.customerName) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(b.outdoorToken) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY b.createdAt DESC")
    List<Bill> searchByText(@Param("q") String q, org.springframework.data.domain.Pageable pageable);

    List<Bill> findByShiftId(Long shiftId);

    @Query("SELECT COUNT(b) FROM Bill b")
    long countTotalBills();

    @Query("SELECT COALESCE(SUM(b.netAmount), 0) FROM Bill b WHERE b.createdAt BETWEEN :start AND :end AND b.status = :status")
    java.math.BigDecimal calculateRevenueBetweenAndStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") Bill.Status status);

    default java.math.BigDecimal calculateRevenueBetween(LocalDateTime start, LocalDateTime end) {
        return calculateRevenueBetweenAndStatus(start, end, Bill.Status.PAID);
    }

    List<Bill> findByCreatedAtBetweenAndStatusOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Bill.Status status);
    List<Bill> findByCreatedAtBetweenAndStatusIn(LocalDateTime start, LocalDateTime end, List<Bill.Status> statuses);

    long countByCreatedAtBetweenAndStatus(LocalDateTime start, LocalDateTime end, Bill.Status status);

    List<Bill> findByStatusInOrderByCreatedAtAsc(List<Bill.Status> statuses);

    Optional<Bill> findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(String tableNumber, List<Bill.Status> statuses);

    /** Row-locks the bill (SELECT ... FOR UPDATE) so concurrent state transitions on the same bill are serialised; other bills are unaffected. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bill b WHERE b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") Long id);

    /** Transaction-scoped advisory lock keyed by an order token; serialises concurrent creation of the same logical order only. */
    @Query(value = "SELECT CAST(1 AS integer) FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS l", nativeQuery = true)
    Integer lockOrderKey(@Param("key") String key);

    Optional<Bill> findByOutdoorToken(String outdoorToken);

    boolean existsByOutdoorToken(String outdoorToken);

    Optional<Bill> findFirstByOutdoorTokenAndStatusInOrderByCreatedAtDesc(String outdoorToken, List<Bill.Status> statuses);

    Optional<Bill> findByIdAndStatusIn(Long id, List<Bill.Status> statuses);

    @Query(value = "SELECT COALESCE(MAX(CAST(NULLIF(regexp_replace(outdoor_token, '^OUT-', ''), '') AS INTEGER)), 0) FROM bills WHERE outdoor_token ~ '^OUT-[0-9]+$'", nativeQuery = true)
    Integer findMaxOutdoorTokenNumber();

    Optional<Bill> findByTakeawayToken(String takeawayToken);

    boolean existsByTakeawayToken(String takeawayToken);

    Optional<Bill> findFirstByTakeawayTokenAndStatusInOrderByCreatedAtDesc(String takeawayToken, List<Bill.Status> statuses);

    @Query(value = "SELECT COALESCE(MAX(CAST(NULLIF(regexp_replace(takeaway_token, '^TAK-', ''), '') AS INTEGER)), 0) FROM bills WHERE takeaway_token ~ '^TAK-[0-9]+$'", nativeQuery = true)
    Integer findMaxTakeawayTokenNumber();
}
