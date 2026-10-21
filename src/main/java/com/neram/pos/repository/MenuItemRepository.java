package com.neram.pos.repository;

import com.neram.pos.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByActiveTrue();
    List<MenuItem> findByCategoryIdAndActiveTrue(Long categoryId);
    List<MenuItem> findByIsAvailableTrueAndActiveTrue();
    List<MenuItem> findByNameIgnoreCase(String name);
}
