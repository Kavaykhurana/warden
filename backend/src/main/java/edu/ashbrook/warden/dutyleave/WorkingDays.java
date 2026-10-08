package edu.ashbrook.warden.dutyleave;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class WorkingDays {

    private WorkingDays() {
    }

    /** Monday–Friday days between {@code start} and {@code end}, both inclusive; 0 when end is before start. */
    public static int between(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return 0;
        }
        return (int) start.datesUntil(end.plusDays(1))
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
                .count();
    }
}
