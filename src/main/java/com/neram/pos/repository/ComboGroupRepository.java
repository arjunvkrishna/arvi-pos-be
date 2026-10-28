package com.neram.pos.repository;

import com.neram.pos.entity.ComboGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComboGroupRepository extends JpaRepository<ComboGroup, Long> {
    List<ComboGroup> findByComboMenuItemIdOrderBySortOrderAsc(Long comboMenuItemId);
}
