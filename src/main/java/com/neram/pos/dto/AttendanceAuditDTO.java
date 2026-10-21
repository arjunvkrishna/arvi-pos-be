package com.neram.pos.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAuditDTO {
    private LocalDate date;
    private String name;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime checkIn;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime checkOut;
}
