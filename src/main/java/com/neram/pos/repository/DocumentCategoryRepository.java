package com.neram.pos.repository;

import com.neram.pos.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {
    List<DocumentCategory> findAllByOrderByNameAsc();
}
