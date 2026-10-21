package com.neram.pos.repository;

import com.neram.pos.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrueOrderBySortOrderAsc();
    Optional<Category> findByNameIgnoreCase(String name);
    List<Category> findAllByNameIgnoreCase(String name);
}
