package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceMonthlySummaryDTO {
    private Long employeeId;
    private String employeeName;
    private String role;
    private LocalDate joinedDate;
    private int year;
    private int month;
    private int totalDaysInMonth;
    private int eligibleDays;
    private long presentDays;
    private long halfDays;
    private long absentDays;
    private long leaveDays;
    private double effectivePresentDays;
    private double attendancePercentage;
}
