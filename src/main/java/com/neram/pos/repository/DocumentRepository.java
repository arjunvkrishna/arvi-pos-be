package com.neram.pos.repository;

import com.neram.pos.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("SELECT d FROM Document d JOIN FETCH d.category LEFT JOIN FETCH d.attachments WHERE d.isActive = true ORDER BY d.createdAt DESC")
    List<Document> findAllActiveWithDetails();

    @Query("SELECT d FROM Document d JOIN FETCH d.category LEFT JOIN FETCH d.attachments WHERE d.category.id = :categoryId AND d.isActive = true ORDER BY d.createdAt DESC")
    List<Document> findByCategoryIdAndActive(@Param("categoryId") Long categoryId);

    @Query("SELECT d FROM Document d JOIN FETCH d.category LEFT JOIN FETCH d.attachments WHERE d.id = :id")
    Optional<Document> findByIdWithDetails(@Param("id") Long id);
}
