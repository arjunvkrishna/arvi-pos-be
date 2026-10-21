package com.neram.pos.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AttendanceRequestDTO {
    private Long employeeId;
    private LocalDate date;
    private LocalTime checkIn;
    private LocalTime checkOut;
    private String status;
    private String notes;
}
