package com.neram.pos.repository;

import com.neram.pos.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long>, JpaSpecificationExecutor<InventoryTransaction> {
    List<InventoryTransaction> findTop200ByOrderByCreatedAtDesc();
    List<InventoryTransaction> findByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<InventoryTransaction> findByItemIdOrderByCreatedAtDesc(Long itemId);
    List<InventoryTransaction> findByBillId(Long billId);
}
