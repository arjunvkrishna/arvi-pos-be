package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "discount_percentage", nullable = false)
    private Double discountPercentage;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Happy hour: when true the promotion only applies on the chosen weekdays between dailyStart and dailyEnd, inside the date range above. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean recurring = false;

    /** ISO weekdays, 1 = Monday ... 7 = Sunday, comma separated. */
    @Column(name = "days_of_week", length = 20)
    private String daysOfWeek;

    @Column(name = "daily_start")
    private LocalTime dailyStart;

    /** When earlier than dailyStart the window runs past midnight. */
    @Column(name = "daily_end")
    private LocalTime dailyEnd;
}
