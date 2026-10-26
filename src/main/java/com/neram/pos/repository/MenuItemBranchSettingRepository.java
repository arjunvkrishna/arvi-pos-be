package com.neram.pos.repository;

import com.neram.pos.entity.MenuItemBranchSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemBranchSettingRepository extends JpaRepository<MenuItemBranchSetting, Long> {
    List<MenuItemBranchSetting> findByOutletId(Long outletId);

    Optional<MenuItemBranchSetting> findByMenuItemIdAndOutletId(Long menuItemId, Long outletId);
}
