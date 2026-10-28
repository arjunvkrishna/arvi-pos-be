package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAttachmentDTO {
    private Long id;
    private Long documentId;
    private String filename;
    private String contentType;
    private Long fileSizeBytes;
    private Long uploadedBy;
    private Instant uploadedAt;
}
