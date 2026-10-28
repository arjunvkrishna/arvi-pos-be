package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    private LocalDate birthday;
    private LocalDate anniversary;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "marketing_consent", nullable = false)
    @Builder.Default
    private Boolean marketingConsent = false;

    @Column(name = "unsubscribe_token", nullable = false, unique = true, length = 64)
    @Builder.Default
    private String unsubscribeToken = UUID.randomUUID().toString().replace("-", "");

    @Column(name = "points_balance", nullable = false)
    @Builder.Default
    private Integer pointsBalance = 0;

    /** Last day the points are valid; every purchase pushes it forward. Null while the customer holds no points. */
    @Column(name = "points_expire_at")
    private LocalDate pointsExpireAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void onPrePersist() {
        if (unsubscribeToken == null || unsubscribeToken.isBlank()) {
            unsubscribeToken = UUID.randomUUID().toString().replace("-", "");
        }
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
