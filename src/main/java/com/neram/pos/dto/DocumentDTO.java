package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDTO {
    private Long id;
    private String title;
    private String content;
    private Long categoryId;
    private String categoryName;
    private List<String> tags;
    private Boolean isActive;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private List<DocumentAttachmentDTO> attachments;
}
