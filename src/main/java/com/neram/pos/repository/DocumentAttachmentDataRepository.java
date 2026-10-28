package com.neram.pos.repository;

import com.neram.pos.entity.DocumentAttachmentData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentAttachmentDataRepository extends JpaRepository<DocumentAttachmentData, Long> {
}
