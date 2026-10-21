package com.neram.pos.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// ─── Auth DTOs ───────────────────────────────────────────────────────────────
@Data
public class LoginRequest {
    private String username;
    private String password;
}

// ─── Generic Response ─────────────────────────────────────────────────────────
// (Separate file kept minimal here — all DTOs aggregated)
