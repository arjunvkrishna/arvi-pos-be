package com.neram.pos.repository;

import com.neram.pos.entity.BillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillItemRepository extends JpaRepository<BillItem, Long> {
    List<BillItem> findByBillId(Long billId);

    @Query("SELECT bi.menuItemId, bi.itemName, SUM(bi.quantity), SUM(bi.subtotal) " +
           "FROM BillItem bi JOIN bi.bill b " +
           "WHERE b.createdAt >= :since AND b.status = 'PAID' " +
           "GROUP BY bi.menuItemId, bi.itemName " +
           "ORDER BY SUM(bi.quantity) DESC")
    List<Object[]> findTopMovingDishesSince(@Param("since") LocalDateTime since);

    @Query("SELECT bi.menuItemId, bi.itemName, SUM(bi.quantity), SUM(bi.subtotal) " +
           "FROM BillItem bi JOIN bi.bill b " +
           "WHERE b.createdAt BETWEEN :start AND :end AND b.status = 'PAID' " +
           "GROUP BY bi.menuItemId, bi.itemName " +
           "ORDER BY SUM(bi.quantity) DESC")
    List<Object[]> findDishSalesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
