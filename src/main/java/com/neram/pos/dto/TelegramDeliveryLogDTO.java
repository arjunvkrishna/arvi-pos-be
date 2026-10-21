package com.neram.pos.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramDeliveryLogDTO {
    private Long id;
    private LocalDate reportDate;
    private String managerName;
    private String chatIdMasked;
    private String status;
    private String errorDescription;
    private Integer itemsCount;
    private String deliveryType;
    private LocalDateTime sentAt;
}
