package com.neram.pos.service;

import com.neram.pos.entity.Promotion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/** When a promotion applies: a one-off date range, or a weekly happy-hour window inside a date range (pure, no database). */
public final class PromotionSchedule {
    private PromotionSchedule() {}

    private record Interval(LocalDateTime from, LocalDateTime to) {
        boolean intersects(Interval o) { return from.isBefore(o.to) && o.from.isBefore(to); }
    }

    public static List<Integer> parseDays(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Integer::valueOf).sorted().distinct().collect(Collectors.toList());
    }

    public static String joinDays(Collection<Integer> days) {
        return days == null ? null : days.stream().sorted().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Throws a message a person can act on when the recurring part of a request is wrong. */
    public static void validateRecurring(List<Integer> days, LocalTime start, LocalTime end) {
        if (days == null || days.isEmpty()) throw new IllegalArgumentException("Choose at least one day of the week for the happy hour.");
        for (Integer d : days) if (d == null || d < 1 || d > 7) throw new IllegalArgumentException("Days of the week must be between 1 (Monday) and 7 (Sunday).");
        if (start == null || end == null) throw new IllegalArgumentException("Choose the time the happy hour starts and ends each day.");
        if (start.equals(end)) throw new IllegalArgumentException("The daily start and end time cannot be the same.");
    }

    private static boolean windowOpenOn(Promotion p, LocalDate day) {
        return parseDays(p.getDaysOfWeek()).contains(day.getDayOfWeek().getValue());
    }

    /** The interval(s) of this promotion that start on the given calendar day, clipped to its date range. */
    private static List<Interval> intervalsStartingOn(Promotion p, LocalDate day) {
        LocalDateTime rangeFrom = p.getStartDateTime(), rangeTo = p.getEndDateTime();
        if (!Boolean.TRUE.equals(p.getRecurring())) {
            return day.equals(rangeFrom.toLocalDate()) ? List.of(new Interval(rangeFrom, rangeTo)) : List.of();
        }
        if (!windowOpenOn(p, day)) return List.of();
        LocalDateTime from = day.atTime(p.getDailyStart());
        LocalDateTime to = p.getDailyEnd().isAfter(p.getDailyStart()) ? day.atTime(p.getDailyEnd()) : day.plusDays(1).atTime(p.getDailyEnd());
        if (from.isBefore(rangeFrom)) from = rangeFrom;
        if (to.isAfter(rangeTo)) to = rangeTo;
        return from.isBefore(to) ? List.of(new Interval(from, to)) : List.of();
    }

    /** Whether the promotion is switched on and applies at this moment. */
    public static boolean isActiveAt(Promotion p, LocalDateTime now) {
        if (!Boolean.TRUE.equals(p.getIsActive())) return false;
        if (now.isBefore(p.getStartDateTime()) || now.isAfter(p.getEndDateTime())) return false;
        if (!Boolean.TRUE.equals(p.getRecurring())) return true;
        LocalTime t = now.toLocalTime(); LocalTime s = p.getDailyStart(), e = p.getDailyEnd();
        if (s.isBefore(e)) return windowOpenOn(p, now.toLocalDate()) && !t.isBefore(s) && t.isBefore(e);
        return (windowOpenOn(p, now.toLocalDate()) && !t.isBefore(s)) || (windowOpenOn(p, now.toLocalDate().minusDays(1)) && t.isBefore(e));
    }

    /** One-off: its whole range (on any day). Recurring: only the windows that start on that day. */
    private static List<Interval> occurrences(Promotion p, LocalDate day) {
        if (!Boolean.TRUE.equals(p.getRecurring())) return List.of(new Interval(p.getStartDateTime(), p.getEndDateTime()));
        return intervalsStartingOn(p, day);
    }

    /** True when the two promotions could ever apply at the same moment (so only one of them may be switched on). */
    public static boolean overlaps(Promotion a, Promotion b) {
        if (a.getStartDateTime().isAfter(b.getEndDateTime()) || b.getStartDateTime().isAfter(a.getEndDateTime())) return false;
        if (!Boolean.TRUE.equals(a.getRecurring()) && !Boolean.TRUE.equals(b.getRecurring())) return true;   // two date ranges that overlap
        LocalDate from = (a.getStartDateTime().isAfter(b.getStartDateTime()) ? a.getStartDateTime() : b.getStartDateTime()).toLocalDate().minusDays(1);
        LocalDate to = (a.getEndDateTime().isBefore(b.getEndDateTime()) ? a.getEndDateTime() : b.getEndDateTime()).toLocalDate();
        int guard = 0;
        for (LocalDate d = from; !d.isAfter(to) && guard++ < 800; d = d.plusDays(1)) {
            for (Interval x : occurrences(a, d)) {
                for (int off = -1; off <= 1; off++) for (Interval y : occurrences(b, d.plusDays(off))) if (x.intersects(y)) return true;
            }
        }
        return false;
    }

    /** "Mon-Fri, 4:00 pm - 7:00 pm"; null for a one-off promotion. */
    public static String scheduleText(Promotion p) {
        if (!Boolean.TRUE.equals(p.getRecurring()) || p.getDailyStart() == null || p.getDailyEnd() == null) return null;
        return dayText(parseDays(p.getDaysOfWeek())) + ", " + clock(p.getDailyStart()) + " - " + clock(p.getDailyEnd());
    }

    static String clock(LocalTime t) {
        int h = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return h + ":" + String.format("%02d", t.getMinute()) + (t.getHour() < 12 ? " am" : " pm");
    }

    static String dayText(List<Integer> days) {
        if (days.size() == 7) return "Every day";
        boolean run = days.size() > 2; for (int i = 1; i < days.size(); i++) if (days.get(i) != days.get(i - 1) + 1) run = false;
        java.util.function.Function<Integer, String> n = d -> DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        if (run) return n.apply(days.get(0)) + "-" + n.apply(days.get(days.size() - 1));
        return days.stream().map(n).collect(Collectors.joining(", "));
    }
}
