package com.neram.pos.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveResponseDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String leaveType;
    private String reason;
    private String status;
    private Long reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private LocalDateTime createdAt;
}
