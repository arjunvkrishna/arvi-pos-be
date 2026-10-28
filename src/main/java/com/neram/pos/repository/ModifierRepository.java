package com.neram.pos.repository;

import com.neram.pos.entity.Modifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModifierRepository extends JpaRepository<Modifier, Long> {
    List<Modifier> findByGroupIdAndIsActiveTrueOrderBySortOrderAsc(Long groupId);
}
