package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String role;
    private Long employeeId;
    private String employeeName;
    private Boolean active;
    private Boolean canTakeOrders;
    private Boolean canManageInventory;
    private Boolean mustChangePassword;
    private LocalDateTime createdAt;
}
