package edu.ashbrook.warden.dutyleave;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class WorkingDaysTest {

    private static int between(String a, String b) {
        return WorkingDays.between(LocalDate.parse(a), LocalDate.parse(b));
    }

    @Test
    void countsMondayToFridayInclusive() {
        assertThat(between("2026-10-12", "2026-10-13")).isEqualTo(2);   // Mon–Tue
        assertThat(between("2026-10-19", "2026-10-23")).isEqualTo(5);   // full week
        assertThat(between("2026-10-16", "2026-10-19")).isEqualTo(2);   // Fri–Mon over a weekend
        assertThat(between("2026-10-05", "2026-10-16")).isEqualTo(10);  // two weeks
    }

    @Test
    void weekendOnlyAndSingleDays() {
        assertThat(between("2026-10-10", "2026-10-11")).isZero();       // Sat–Sun
        assertThat(between("2026-10-14", "2026-10-14")).isEqualTo(1);
        assertThat(between("2026-10-10", "2026-10-10")).isZero();
    }

    @Test
    void endBeforeStartIsZero() {
        assertThat(between("2026-10-13", "2026-10-12")).isZero();
    }
}
