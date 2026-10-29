package com.neram.pos.controller;

import com.neram.pos.dto.DocumentAttachmentDTO;
import com.neram.pos.dto.DocumentCategoryDTO;
import com.neram.pos.dto.DocumentDTO;
import com.neram.pos.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.DOCUMENTS)
public class DocumentController {

    private final DocumentService documentService;

    // --- Categories ---

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<DocumentCategoryDTO>> getCategories() {
        return ResponseEntity.ok(documentService.getCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<DocumentCategoryDTO> createCategory(@RequestBody Map<String, String> request) {
        String name = request != null ? request.get("name") : null;
        return ResponseEntity.ok(documentService.createCategory(name));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SETTINGS')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        documentService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // --- Documents ---

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<DocumentDTO>> getDocuments(@RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(documentService.getDocuments(categoryId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DocumentDTO> createDocument(@RequestBody DocumentDTO dto) {
        return ResponseEntity.ok(documentService.createDocument(dto, null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DocumentDTO> updateDocument(@PathVariable Long id, @RequestBody DocumentDTO dto) {
        return ResponseEntity.ok(documentService.updateDocument(id, dto, null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    // --- Attachments ---

    @PostMapping("/{id}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DocumentAttachmentDTO> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(documentService.uploadAttachment(id, file, null));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        DocumentService.AttachmentPayload payload = documentService.getAttachmentPayload(attachmentId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(payload.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.filename().replace("\"", "\\\"") + "\"")
                .body(payload.resource());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        documentService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }
}
