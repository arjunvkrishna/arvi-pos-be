package com.neram.pos.repository;

import com.neram.pos.entity.ModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, Long> {
    List<ModifierGroup> findByIsActiveTrueOrderByNameAsc();
}
