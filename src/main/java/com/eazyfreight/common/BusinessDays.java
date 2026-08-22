package com.eazyfreight.common;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Business-day arithmetic for the lead-time rules in the specifications, e.g. a
 * requested ETD must be at least five business days out.
 *
 * <p>Weekends only — public holidays are not modelled, which is a known
 * simplification.
 */
public final class BusinessDays {

    private BusinessDays() {
    }

    public static LocalDate add(LocalDate start, int businessDays) {
        LocalDate date = start;
        int added = 0;
        while (added < businessDays) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                added++;
            }
        }
        return date;
    }

    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /** Business days from {@code start} to {@code end}; negative when end precedes start. */
    public static long between(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return -between(end, start);
        }
        long count = 0;
        LocalDate date = start;
        while (date.isBefore(end)) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                count++;
            }
        }
        return count;
    }
}
