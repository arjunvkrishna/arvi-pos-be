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
public class ShiftScheduleDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeRole;
    private Instant startsAt;
    private Instant endsAt;
    private String roleNote;
    private String status;
    private Long createdBy;
    private Instant createdAt;
}
