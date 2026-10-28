package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String channel = "EMAIL"; // EMAIL, SMS

    @Column(name = "audience_type", nullable = false, length = 30)
    private String audienceType; // ALL, LOYALTY_MEMBERS, INACTIVE_30_DAYS

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT, SCHEDULED, SENT

    @Column(name = "sent_count")
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
