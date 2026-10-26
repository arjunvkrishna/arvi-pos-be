package com.neram.pos.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** A reporting period and the period right before it (same length), resolved from the names the analytics screen uses. */
public record AnalyticsPeriod(String name, LocalDate start, LocalDate end, LocalDate prevStart, LocalDate prevEnd) {

    public static AnalyticsPeriod resolve(String periodParam, LocalDate customStart, LocalDate customEnd, LocalDate today) {
        String p = periodParam != null && !periodParam.isBlank() ? periodParam.toUpperCase() : "THIS_MONTH";
        switch (p) {
            case "TODAY": return new AnalyticsPeriod(p, today, today, today.minusDays(1), today.minusDays(1));
            case "YESTERDAY": return new AnalyticsPeriod(p, today.minusDays(1), today.minusDays(1), today.minusDays(2), today.minusDays(2));
            case "THIS_WEEK": {
                LocalDate s = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                long days = ChronoUnit.DAYS.between(s, today) + 1;
                LocalDate pe = s.minusDays(1);
                return new AnalyticsPeriod(p, s, today, pe.minusDays(days - 1), pe);
            }
            case "LAST_WEEK": {
                LocalDate s = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return new AnalyticsPeriod(p, s, s.plusDays(6), s.minusWeeks(1), s.plusDays(6).minusWeeks(1));
            }
            case "LAST_MONTH": {
                LocalDate d = today.minusMonths(1);
                LocalDate s = d.withDayOfMonth(1);
                LocalDate before = d.minusMonths(1);
                return new AnalyticsPeriod(p, s, d.withDayOfMonth(d.lengthOfMonth()), before.withDayOfMonth(1), before.withDayOfMonth(before.lengthOfMonth()));
            }
            case "THIS_YEAR": return new AnalyticsPeriod(p, today.withDayOfYear(1), today, today.minusYears(1).withDayOfYear(1), today.minusYears(1));
            case "LAST_YEAR": {
                LocalDate y = today.minusYears(1);
                LocalDate yy = today.minusYears(2);
                return new AnalyticsPeriod(p, y.withDayOfYear(1), y.withDayOfYear(y.lengthOfYear()), yy.withDayOfYear(1), yy.withDayOfYear(yy.lengthOfYear()));
            }
            case "CUSTOM": {
                LocalDate s = customStart != null ? customStart : today.minusDays(30);
                LocalDate e = customEnd != null ? customEnd : today;
                if (e.isBefore(s)) throw new IllegalArgumentException("The end date cannot be before the start date.");
                long days = ChronoUnit.DAYS.between(s, e) + 1;
                LocalDate pe = s.minusDays(1);
                return new AnalyticsPeriod(p, s, e, pe.minusDays(days - 1), pe);
            }
            default: {
                LocalDate s = today.withDayOfMonth(1);
                LocalDate lm = today.minusMonths(1);
                return new AnalyticsPeriod("THIS_MONTH", s, today, lm.withDayOfMonth(1), lm.withDayOfMonth(Math.min(today.getDayOfMonth(), lm.lengthOfMonth())));
            }
        }
    }
}
