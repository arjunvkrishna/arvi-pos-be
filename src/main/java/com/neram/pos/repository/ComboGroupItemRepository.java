package com.neram.pos.repository;

import com.neram.pos.entity.ComboGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComboGroupItemRepository extends JpaRepository<ComboGroupItem, Long> {
    List<ComboGroupItem> findByGroupId(Long groupId);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM ComboGroupItem c WHERE c.group.comboMenuItemId = :dishId AND c.menuItemId = :menuItemId")
    List<ComboGroupItem> findByDishIdAndChildId(@org.springframework.data.repository.query.Param("dishId") Long dishId, @org.springframework.data.repository.query.Param("menuItemId") Long menuItemId);
}
