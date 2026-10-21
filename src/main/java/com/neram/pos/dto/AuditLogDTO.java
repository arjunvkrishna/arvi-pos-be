package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private String action;
    private String entityType;
    private Long entityId;
    private String oldValue;
    private String newValue;
    private Long performedBy;
    private String performedByName;
    private String ipAddress;
    private LocalDateTime performedAt;

    public String getCategory() {
        return entityType;
    }
}
