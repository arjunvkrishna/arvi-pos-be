package com.neram.pos.repository;

import com.neram.pos.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<InventoryItem> {
    List<InventoryItem> findByActiveTrue();

    Optional<InventoryItem> findByNameIgnoreCaseAndActiveTrue(String name);

    List<InventoryItem> findByItemCategoryIgnoreCase(String itemCategory);

    List<InventoryItem> findByItemCategoryIgnoreCaseAndActiveTrue(String itemCategory);

    @Query("SELECT i FROM InventoryItem i WHERE i.active = true AND i.quantity <= i.minQuantity")
    List<InventoryItem> findLowStockItems();
}
