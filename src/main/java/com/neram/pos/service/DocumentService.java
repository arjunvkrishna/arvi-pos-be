package com.neram.pos.service;

import com.neram.pos.dto.DocumentAttachmentDTO;
import com.neram.pos.dto.DocumentCategoryDTO;
import com.neram.pos.dto.DocumentDTO;
import com.neram.pos.entity.Document;
import com.neram.pos.entity.DocumentAttachment;
import com.neram.pos.entity.DocumentAttachmentData;
import com.neram.pos.entity.DocumentCategory;
import com.neram.pos.repository.DocumentAttachmentDataRepository;
import com.neram.pos.repository.DocumentAttachmentRepository;
import com.neram.pos.repository.DocumentCategoryRepository;
import com.neram.pos.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    public static final long MAX_FILE_SIZE = 5L * 1024L * 1024L; // 5 MB
    public static final int MAX_ATTACHMENTS_PER_DOCUMENT = 10;

    private static final java.util.Map<String, String> ALLOWED_EXTENSIONS_MAP = java.util.Map.ofEntries(
            java.util.Map.entry("pdf", "application/pdf"),
            java.util.Map.entry("png", "image/png"),
            java.util.Map.entry("jpg", "image/jpeg"),
            java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("webp", "image/webp"),
            java.util.Map.entry("csv", "text/csv"),
            java.util.Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            java.util.Map.entry("xls", "application/vnd.ms-excel"),
            java.util.Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            java.util.Map.entry("doc", "application/msword"),
            java.util.Map.entry("txt", "text/plain")
    );

    private final DocumentCategoryRepository documentCategoryRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository documentAttachmentRepository;
    private final DocumentAttachmentDataRepository documentAttachmentDataRepository;

    // --- Categories ---

    @Transactional(readOnly = true)
    public List<DocumentCategoryDTO> getCategories() {
        return documentCategoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> DocumentCategoryDTO.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .createdAt(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentCategoryDTO createCategory(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty.");
        }
        DocumentCategory cat = documentCategoryRepository.save(DocumentCategory.builder()
                .name(name.trim())
                .createdAt(Instant.now())
                .build());
        return DocumentCategoryDTO.builder()
                .id(cat.getId())
                .name(cat.getName())
                .createdAt(cat.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteCategory(Long id) {
        documentCategoryRepository.deleteById(id);
    }

    // --- Documents ---

    @Transactional(readOnly = true)
    public List<DocumentDTO> getDocuments(Long categoryId) {
        List<Document> docs = (categoryId != null && categoryId > 0)
                ? documentRepository.findByCategoryIdAndActive(categoryId)
                : documentRepository.findAllActiveWithDetails();

        return docs.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentDTO getDocumentById(Long id) {
        Document doc = documentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + id));
        return toDTO(doc);
    }

    @Transactional
    public DocumentDTO createDocument(DocumentDTO dto, Long userId) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Document title is required.");
        }
        DocumentCategory category = documentCategoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + dto.getCategoryId()));

        String[] tags = dto.getTags() != null ? dto.getTags().toArray(new String[0]) : new String[0];

        Document doc = Document.builder()
                .title(dto.getTitle().trim())
                .content(dto.getContent())
                .category(category)
                .tags(tags)
                .isActive(true)
                .createdBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return toDTO(documentRepository.save(doc));
    }

    @Transactional
    public DocumentDTO updateDocument(Long id, DocumentDTO dto, Long userId) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + id));
        assertMayChange(doc);

        if (dto.getTitle() != null && !dto.getTitle().trim().isEmpty()) {
            doc.setTitle(dto.getTitle().trim());
        }
        if (dto.getContent() != null) {
            doc.setContent(dto.getContent());
        }
        if (dto.getCategoryId() != null) {
            DocumentCategory cat = documentCategoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + dto.getCategoryId()));
            doc.setCategory(cat);
        }
        if (dto.getTags() != null) {
            doc.setTags(dto.getTags().toArray(new String[0]));
        }
        if (dto.getIsActive() != null) {
            doc.setIsActive(dto.getIsActive());
        }
        doc.setUpdatedAt(Instant.now());

        return toDTO(documentRepository.save(doc));
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + id));
        assertMayChange(doc);
        doc.setIsActive(false);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
    }

    // --- Attachments ---

    @Transactional
    public DocumentAttachmentDTO uploadAttachment(Long documentId, MultipartFile file, Long userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(String.format("File size (%d bytes) exceeds the 5MB maximum limit.", file.getSize()));
        }

        if (documentAttachmentRepository.countByDocumentId(documentId) >= MAX_ATTACHMENTS_PER_DOCUMENT) {
            throw new IllegalArgumentException(String.format("Maximum of %d attachments allowed per document.", MAX_ATTACHMENTS_PER_DOCUMENT));
        }

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + documentId));
        assertMayChange(doc);

        String rawFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document-file";
        String filename = java.nio.file.Paths.get(rawFilename).getFileName().toString();

        String ext = "";
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < filename.length() - 1) {
            ext = filename.substring(dotIdx + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS_MAP.containsKey(ext)) {
            throw new IllegalArgumentException("File type not allowed. Permitted formats: PDF, PNG, JPG, JPEG, WEBP, CSV, XLSX, XLS, DOCX, DOC, TXT");
        }

        String canonicalContentType = ALLOWED_EXTENSIONS_MAP.get(ext);

        DocumentAttachment attachment = DocumentAttachment.builder()
                .document(doc)
                .filename(filename)
                .contentType(canonicalContentType)
                .fileSizeBytes(file.getSize())
                .uploadedBy(userId)
                .uploadedAt(Instant.now())
                .build();

        DocumentAttachment savedAttachment = documentAttachmentRepository.save(attachment);

        DocumentAttachmentData data = DocumentAttachmentData.builder()
                .attachmentId(savedAttachment.getId())
                .fileData(file.getBytes())
                .build();
        documentAttachmentDataRepository.save(data);

        return toAttachmentDTO(savedAttachment);
    }

    @Transactional(readOnly = true)
    public AttachmentPayload getAttachmentPayload(Long attachmentId) {
        DocumentAttachment attachment = documentAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found with id: " + attachmentId));
        // An attachment id alone says nothing about whose it is: the parent document decides who may open it.
        assertMayRead(attachment.getDocument());

        DocumentAttachmentData data = documentAttachmentDataRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment data payload missing for id: " + attachmentId));

        return new AttachmentPayload(
                attachment.getFilename(),
                attachment.getContentType(),
                new ByteArrayResource(data.getFileData())
        );
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) {
        DocumentAttachment attachment = documentAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found with id: " + attachmentId));
        assertMayChange(attachment.getDocument());
        documentAttachmentDataRepository.deleteById(attachmentId);
        documentAttachmentRepository.deleteById(attachmentId);
    }

    /**
     * Touching a document loads it, and loading refuses another branch's document (see OutletScopeListener). A document
     * with no branch belongs to the whole cafe: branch-level users may read it but only the owner and main managers
     * (who run without a branch limit) may change it.
     */
    private static void assertMayRead(Document doc) {
        if (doc != null) {
            doc.getOutletId();
        }
    }

    private static void assertMayChange(Document doc) {
        assertMayRead(doc);
        boolean branchLevelCaller = com.neram.pos.access.OutletContext.currentIdOrNull() != null;
        if (doc != null && doc.getOutletId() == null && branchLevelCaller) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This document belongs to the whole cafe; only the owner or main manager can change it.");
        }
    }

    public DocumentDTO toDTO(Document doc) {
        List<String> tags = (doc.getTags() != null) ? Arrays.asList(doc.getTags()) : Collections.emptyList();
        List<DocumentAttachmentDTO> attachments = (doc.getAttachments() != null)
                ? doc.getAttachments().stream().map(this::toAttachmentDTO).collect(Collectors.toList())
                : Collections.emptyList();

        return DocumentDTO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .categoryId(doc.getCategory() != null ? doc.getCategory().getId() : null)
                .categoryName(doc.getCategory() != null ? doc.getCategory().getName() : null)
                .tags(tags)
                .isActive(doc.getIsActive())
                .createdBy(doc.getCreatedBy())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .attachments(attachments)
                .build();
    }

    public DocumentAttachmentDTO toAttachmentDTO(DocumentAttachment a) {
        return DocumentAttachmentDTO.builder()
                .id(a.getId())
                .documentId(a.getDocument() != null ? a.getDocument().getId() : null)
                .filename(a.getFilename())
                .contentType(a.getContentType())
                .fileSizeBytes(a.getFileSizeBytes())
                .uploadedBy(a.getUploadedBy())
                .uploadedAt(a.getUploadedAt())
                .build();
    }

    public record AttachmentPayload(String filename, String contentType, Resource resource) {}
}
