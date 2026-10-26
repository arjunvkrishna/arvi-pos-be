package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String address;

    @Column(name = "gst_number")
    private String gstNumber;

    /** A short line or quote shown under the cafe name (public menu, login screen). */
    @Column(length = 200)
    private String tagline;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "subscription_plan", nullable = false)
    @Builder.Default
    private String subscriptionPlan = "PRO";

    @Column(nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "max_outlets", nullable = false)
    @Builder.Default
    private Integer maxOutlets = 5;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "currency_symbol", nullable = false)
    @Builder.Default
    private String currencySymbol = "₹";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
