package com.neram.pos.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LeaveApplicationRequestDTO {
    private Long employeeId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String leaveType;
    private String reason;
}
