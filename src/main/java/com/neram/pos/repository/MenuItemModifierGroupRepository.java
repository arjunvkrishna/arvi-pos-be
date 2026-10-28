package com.neram.pos.repository;

import com.neram.pos.entity.MenuItemModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemModifierGroupRepository extends JpaRepository<MenuItemModifierGroup, MenuItemModifierGroup.PK> {
    List<MenuItemModifierGroup> findByMenuItemId(Long menuItemId);
    void deleteByMenuItemId(Long menuItemId);
}
