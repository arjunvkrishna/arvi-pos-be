package com.neram.pos.dto;

import lombok.Data;

@Data
public class LeaveReviewRequestDTO {
    private String status; // APPROVED, REJECTED
    private String reviewNotes;
}
