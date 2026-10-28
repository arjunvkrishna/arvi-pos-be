package com.neram.pos.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The trading day rolls over at 04:00 IST: late-night bills belong to the day before. */
class BusinessDateTest {

    private static Instant ist(String isoLocal) {
        return ZonedDateTime.parse(isoLocal + "+05:30[Asia/Kolkata]").toInstant();
    }

    @Test
    void billAt0230BelongsToThePreviousDay() {
        assertEquals(LocalDate.of(2026, 10, 25), BusinessDate.from(ist("2026-10-26T02:30:00")));
    }

    @Test
    void justBeforeAndAtTheCutoff() {
        assertEquals(LocalDate.of(2026, 10, 25), BusinessDate.from(ist("2026-10-26T03:59:59")));
        assertEquals(LocalDate.of(2026, 10, 26), BusinessDate.from(ist("2026-10-26T04:00:00")));
    }

    @Test
    void afternoonIsTheSameDayAndMidnightIsPrevious() {
        assertEquals(LocalDate.of(2026, 10, 26), BusinessDate.from(ist("2026-10-26T15:00:00")));
        assertEquals(LocalDate.of(2026, 10, 25), BusinessDate.from(ist("2026-10-26T00:00:00")));
    }

    @Test
    void utcInstantIsConvertedToIstFirst() {
        // 21:30 UTC on the 25th is 03:00 IST on the 26th -> still the 25th's trading day
        assertEquals(LocalDate.of(2026, 10, 25), BusinessDate.from(Instant.parse("2026-10-25T21:30:00Z")));
        // 22:30 UTC on the 25th is 04:00 IST on the 26th -> new trading day
        assertEquals(LocalDate.of(2026, 10, 26), BusinessDate.from(Instant.parse("2026-10-25T22:30:00Z")));
    }
}
