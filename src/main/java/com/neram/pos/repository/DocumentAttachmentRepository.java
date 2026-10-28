package com.neram.pos.repository;

import com.neram.pos.entity.DocumentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, Long> {
    List<DocumentAttachment> findByDocumentIdOrderByUploadedAtAsc(Long documentId);
    long countByDocumentId(Long documentId);
}
