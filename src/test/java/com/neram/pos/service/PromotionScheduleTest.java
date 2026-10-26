package com.neram.pos.service;

import com.neram.pos.entity.Promotion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Happy hour: weekly windows inside a date range. 2026-10-19 is a Monday. */
class PromotionScheduleTest {

    private static LocalDateTime at(int day, int h, int m) { return LocalDateTime.of(2026, 10, day, h, m); }
    private static Promotion oneOff(int fromDay, int toDay) {
        return Promotion.builder().isActive(true).recurring(false).startDateTime(at(fromDay, 0, 0)).endDateTime(at(toDay, 23, 59)).build();
    }
    private static Promotion weekly(String days, String from, String to) {
        return Promotion.builder().isActive(true).recurring(true).daysOfWeek(days).dailyStart(LocalTime.parse(from)).dailyEnd(LocalTime.parse(to))
                .startDateTime(LocalDateTime.of(2026, 10, 1, 0, 0)).endDateTime(LocalDateTime.of(2026, 12, 31, 23, 59)).build();
    }

    @Test
    void aWeeklyWindowAppliesOnItsDaysAndTimesOnly() {
        Promotion p = weekly("1,2,3,4,5", "16:00", "19:00");     // Mon-Fri 4-7 pm
        assertTrue(PromotionSchedule.isActiveAt(p, at(19, 16, 0)));    // Monday, start is inclusive
        assertTrue(PromotionSchedule.isActiveAt(p, at(19, 18, 59)));
        assertFalse(PromotionSchedule.isActiveAt(p, at(19, 19, 0)));   // end is exclusive
        assertFalse(PromotionSchedule.isActiveAt(p, at(19, 15, 59)));
        assertFalse(PromotionSchedule.isActiveAt(p, at(24, 17, 0)));   // Saturday
        assertTrue(PromotionSchedule.isActiveAt(p, at(23, 17, 0)));    // Friday
    }

    @Test
    void anOvernightWindowCountsTheMorningAfterAsPartOfTheNightBefore() {
        Promotion p = weekly("5", "22:00", "02:00");                    // Friday night 10 pm - 2 am
        assertTrue(PromotionSchedule.isActiveAt(p, at(23, 23, 0)));    // Friday 11 pm
        assertTrue(PromotionSchedule.isActiveAt(p, at(24, 1, 30)));    // Saturday 1:30 am still Friday's window
        assertFalse(PromotionSchedule.isActiveAt(p, at(24, 2, 0)));
        assertFalse(PromotionSchedule.isActiveAt(p, at(24, 23, 0)));   // Saturday night is not a chosen day
        assertFalse(PromotionSchedule.isActiveAt(p, at(23, 1, 30)));   // Friday 1:30 am belongs to Thursday night
    }

    @Test
    void theDateRangeAndTheSwitchStillApply() {
        Promotion p = weekly("1,2,3,4,5,6,7", "00:00", "23:59");
        p.setStartDateTime(at(20, 0, 0)); p.setEndDateTime(at(22, 23, 59));
        assertFalse(PromotionSchedule.isActiveAt(p, at(19, 12, 0)));
        assertTrue(PromotionSchedule.isActiveAt(p, at(21, 12, 0)));
        assertFalse(PromotionSchedule.isActiveAt(p, at(23, 12, 0)));
        p.setIsActive(false);
        assertFalse(PromotionSchedule.isActiveAt(p, at(21, 12, 0)));
    }

    @Test
    void aOneOffPromotionAppliesThroughoutItsRange() {
        Promotion p = oneOff(20, 22);
        assertTrue(PromotionSchedule.isActiveAt(p, at(21, 3, 0)));
        assertFalse(PromotionSchedule.isActiveAt(p, at(23, 3, 0)));
    }

    @Test
    void overlapWhenWindowsShareATimeOnASharedDay() {
        assertTrue(PromotionSchedule.overlaps(weekly("1,2,3", "16:00", "19:00"), weekly("3,4", "18:00", "20:00")));    // Wednesday 6-7
        assertFalse(PromotionSchedule.overlaps(weekly("1,2,3", "16:00", "19:00"), weekly("3,4", "19:00", "21:00")));   // touching is fine
        assertFalse(PromotionSchedule.overlaps(weekly("1,2", "16:00", "19:00"), weekly("3,4", "16:00", "19:00")));     // different days
        assertFalse(PromotionSchedule.overlaps(weekly("1", "09:00", "11:00"), weekly("1", "16:00", "19:00")));         // different times
    }

    @Test
    void anOvernightWindowOverlapsTheEarlyMorningOfTheNextDay() {
        assertTrue(PromotionSchedule.overlaps(weekly("5", "22:00", "02:00"), weekly("6", "01:00", "03:00")));
        assertFalse(PromotionSchedule.overlaps(weekly("5", "22:00", "02:00"), weekly("5", "01:00", "03:00")));
    }

    @Test
    void aOneOffRangeOnlyOverlapsAWeeklyWindowThatReallyOccursInsideIt() {
        assertTrue(PromotionSchedule.overlaps(oneOff(19, 21), weekly("1,2,3", "16:00", "19:00")));      // Mon-Wed in range
        assertFalse(PromotionSchedule.overlaps(oneOff(19, 21), weekly("6,7", "16:00", "19:00")));       // weekend only, range is Mon-Wed
        assertTrue(PromotionSchedule.overlaps(oneOff(20, 25), oneOff(24, 28)));                          // two date ranges
        assertFalse(PromotionSchedule.overlaps(oneOff(20, 21), oneOff(23, 24)));
    }

    @Test
    void validationAndWording() {
        assertThrows(IllegalArgumentException.class, () -> PromotionSchedule.validateRecurring(List.of(), LocalTime.of(16, 0), LocalTime.of(19, 0)));
        assertThrows(IllegalArgumentException.class, () -> PromotionSchedule.validateRecurring(List.of(8), LocalTime.of(16, 0), LocalTime.of(19, 0)));
        assertThrows(IllegalArgumentException.class, () -> PromotionSchedule.validateRecurring(List.of(1), null, LocalTime.of(19, 0)));
        assertThrows(IllegalArgumentException.class, () -> PromotionSchedule.validateRecurring(List.of(1), LocalTime.of(16, 0), LocalTime.of(16, 0)));
        assertDoesNotThrow(() -> PromotionSchedule.validateRecurring(List.of(1, 5), LocalTime.of(22, 0), LocalTime.of(2, 0)));
        assertEquals("Mon-Fri, 4:00 pm - 7:00 pm", PromotionSchedule.scheduleText(weekly("1,2,3,4,5", "16:00", "19:00")));
        assertEquals("Every day, 12:00 am - 11:30 am", PromotionSchedule.scheduleText(weekly("1,2,3,4,5,6,7", "00:00", "11:30")));
        assertEquals("Mon, Sat, 9:05 am - 10:00 am", PromotionSchedule.scheduleText(weekly("6,1", "09:05", "10:00")));
        assertNull(PromotionSchedule.scheduleText(oneOff(20, 21)));
        assertEquals(List.of(1, 3, 5), PromotionSchedule.parseDays("5,1,3,3"));
        assertEquals("1,3,5", PromotionSchedule.joinDays(List.of(5, 1, 3, 3)));
    }
}
