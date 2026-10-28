package com.neram.pos.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The cafe's trading day: it runs on Asia/Kolkata time and a day only rolls over at 04:00,
 * so a bill rung at 02:30 belongs to the previous calendar day (matches the V74 SQL backfill/default).
 */
public final class BusinessDate {

    public static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");
    public static final int CUTOFF_HOUR = 4;

    private BusinessDate() {}

    public static LocalDate from(Instant instant) {
        ZonedDateTime ist = instant.atZone(ZONE_IST);
        return ist.getHour() < CUTOFF_HOUR ? ist.toLocalDate().minusDays(1) : ist.toLocalDate();
    }

    public static LocalDate now() {
        return from(Instant.now());
    }
}
