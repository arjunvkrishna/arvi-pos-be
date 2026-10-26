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

    /** Newest closed bills first (soft-deleted rows are already excluded by the entity restriction). */
    List<Bill> findTop5ByStatusInOrderByCreatedAtDescIdDesc(List<Bill.Status> statuses);


    // ------------------------------------------------------------------------------------------------------------
    // Bill Log (paged). Native on purpose: the Bill entity hides soft-deleted rows from every other query, but the log
    // shows them as inactive rows. Filters use "empty / 0" instead of NULL so the parameters always have a type:
    //   :loc ''(all) | TABLE | OUTDOOR | TAKEAWAY      :staffId 0(all) | user id | -1(nobody)     :staffUnknown bills with no known creator
    //   :q ''(no search) or a lower-case LIKE pattern (see LikeSearch.pattern)
    // ------------------------------------------------------------------------------------------------------------
    /** Native queries bypass the Hibernate branch filter, so they carry the same rule themselves. */
    String OUTLET_PRED = "(CAST(:#{T(com.neram.pos.access.OutletContext).currentIdOrNull()} AS bigint) IS NULL "
            + "OR b.outlet_id = CAST(:#{T(com.neram.pos.access.OutletContext).currentIdOrNull()} AS bigint) OR b.outlet_id IS NULL) ";

    String LOG_FILTER =
            "WHERE b.created_at BETWEEN :start AND :end AND " + OUTLET_PRED
            + "AND (:loc = '' "
            + "  OR (:loc = 'OUTDOOR' AND (b.order_type = 'OUTDOOR' OR COALESCE(b.outdoor_token, '') <> '')) "
            + "  OR (:loc = 'TAKEAWAY' AND (b.order_type = 'TAKEAWAY' OR COALESCE(b.takeaway_token, '') <> '')) "
            + "  OR (:loc = 'TABLE' AND b.order_type NOT IN ('OUTDOOR', 'TAKEAWAY') AND COALESCE(b.outdoor_token, '') = '' "
            + "      AND COALESCE(b.takeaway_token, '') = '' AND COALESCE(b.table_number, '') <> '')) "
            + "AND ((:staffId = 0 AND :staffUnknown = false) "
            + "  OR (:staffId > 0 AND (b.created_by = :staffId OR b.printed_by = :staffId)) "
            + "  OR (:staffUnknown = true AND (b.created_by IS NULL OR NOT EXISTS (SELECT 1 FROM users cu WHERE cu.id = b.created_by)))) "
            + "AND (:q = '' "
            + "  OR LOWER(b.bill_number) LIKE :q ESCAPE '!' "
            + "  OR LOWER(COALESCE(b.customer_name, '')) LIKE :q ESCAPE '!' "
            + "  OR LOWER(COALESCE(b.outdoor_token, '')) LIKE :q ESCAPE '!' "
            + "  OR LOWER(COALESCE(b.takeaway_token, '')) LIKE :q ESCAPE '!' "
            + "  OR LOWER(COALESCE(b.table_number, '')) LIKE :q ESCAPE '!' "
            + "  OR EXISTS (SELECT 1 FROM users su WHERE (su.id = b.created_by OR su.id = b.printed_by) AND LOWER(su.username) LIKE :q ESCAPE '!') "
            + "  OR ('staff' LIKE :q ESCAPE '!' AND (b.created_by IS NULL OR NOT EXISTS (SELECT 1 FROM users cu2 WHERE cu2.id = b.created_by)))) ";

    @Query(value = "SELECT b.* FROM bills b " + LOG_FILTER + "ORDER BY b.created_at DESC, b.id DESC",
            countQuery = "SELECT COUNT(*) FROM bills b " + LOG_FILTER,
            nativeQuery = true)
    org.springframework.data.domain.Page<Bill> searchBillLog(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                                              @Param("loc") String loc, @Param("staffId") long staffId,
                                                              @Param("staffUnknown") boolean staffUnknown, @Param("q") String q,
                                                              org.springframework.data.domain.Pageable pageable);

    /** Bill count and revenue (net minus successful refunds, paid and partly refunded bills) of the live bills matching the log filters. */
    @Query(value = "SELECT COUNT(*) AS billCount, "
            + "COALESCE(SUM(CASE WHEN b.status IN ('PAID', 'PARTIALLY_REFUNDED') THEN b.net_amount - "
            + "  COALESCE((SELECT SUM(r.amount) FROM bill_refunds r WHERE r.bill_id = b.id AND r.status = 'SUCCESS'), 0) ELSE 0 END), 0) AS revenue "
            + "FROM bills b " + LOG_FILTER + "AND b.deleted_at IS NULL",
            nativeQuery = true)
    BillLogTotals summarizeBillLog(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                   @Param("loc") String loc, @Param("staffId") long staffId,
                                   @Param("staffUnknown") boolean staffUnknown, @Param("q") String q);

    interface BillLogTotals {
        Long getBillCount();
        java.math.BigDecimal getRevenue();
    }

    /** Usernames that created or printed a bill in the period (the Bill Log's staff filter list). */
    @Query(value = "SELECT DISTINCT u.username FROM users u JOIN bills b ON (u.id = b.created_by OR u.id = b.printed_by) "
            + "WHERE b.created_at BETWEEN :start AND :end AND " + OUTLET_PRED + "ORDER BY u.username", nativeQuery = true)
    List<String> findBillLogStaffNames(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT COUNT(*) > 0 FROM bills b WHERE b.created_at BETWEEN :start AND :end AND " + OUTLET_PRED
            + "AND (b.created_by IS NULL OR NOT EXISTS (SELECT 1 FROM users cu WHERE cu.id = b.created_by))", nativeQuery = true)
    boolean existsBillLogUnknownCreator(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Every bill number ever issued in this cafe, soft-deleted ones too (the unique constraint covers them). */
    @Query(value = "SELECT COUNT(*) > 0 FROM bills WHERE bill_number = :number", nativeQuery = true)
    boolean billNumberTaken(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM bills", nativeQuery = true)
    long countAllBillsIncludingDeleted();

    List<Bill> findByShiftId(Long shiftId);

    /** Tips received per staff member (the one who took the order), all time, paid bills only. */
    @Query("SELECT b.createdBy, COALESCE(SUM(b.tipAmount), 0) FROM Bill b WHERE b.status = com.neram.pos.entity.Bill.Status.PAID AND b.createdBy IS NOT NULL GROUP BY b.createdBy")
    List<Object[]> sumTipsByCreator();

    @Query("SELECT COUNT(b) FROM Bill b")
    long countTotalBills();

    @Query("SELECT COALESCE(SUM(b.netAmount), 0) FROM Bill b WHERE b.createdAt BETWEEN :start AND :end AND b.status = :status")
    java.math.BigDecimal calculateRevenueBetweenAndStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") Bill.Status status);

    default java.math.BigDecimal calculateRevenueBetween(LocalDateTime start, LocalDateTime end) {
        return calculateRevenueBetweenAndStatus(start, end, Bill.Status.PAID);
    }

    List<Bill> findByCreatedAtBetweenAndStatusOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Bill.Status status);
    org.springframework.data.domain.Page<Bill> findByCreatedAtBetweenAndStatusOrderByCreatedAtDescIdDesc(LocalDateTime start, LocalDateTime end, Bill.Status status, org.springframework.data.domain.Pageable pageable);
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
