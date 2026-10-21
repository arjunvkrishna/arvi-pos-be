package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequestDTO {
    private String username;
    private String password;
    private String role; // ADMIN, MANAGER, EMPLOYEE
    private Long employeeId; // Optional: link to an existing employee
    private Boolean canTakeOrders;
    private Boolean canManageInventory;
}
