package com.neram.pos.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// ─── Auth DTOs ───────────────────────────────────────────────────────────────
@Data
public class LoginRequest {
    /** Code of the cafe to log in to. Left blank only by SaaS owner (platform) accounts. */
    @com.fasterxml.jackson.annotation.JsonAlias({"cafeCode", "tenant"})
    private String tenantCode;
    private String username;
    private String password;
}

// ─── Generic Response ─────────────────────────────────────────────────────────
// (Separate file kept minimal here — all DTOs aggregated)
