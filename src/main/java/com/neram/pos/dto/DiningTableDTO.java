package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiningTableDTO {
    private Long id;
    private String name;
    private String floor;
    private Boolean active;
    private Integer displayOrder;
    private LocalDateTime createdAt;
}
