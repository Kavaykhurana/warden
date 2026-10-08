package edu.ashbrook.warden.academics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttendanceMathTest {

    @Test
    void aaravsDbmsAttendance() {
        assertThat(AttendanceMath.percentage(42, 29, 0)).isEqualTo(69.0);
        assertThat(AttendanceMath.shortage(69.0)).isTrue();
        assertThat(AttendanceMath.classesNeededFor75(42, 29, 0)).isEqualTo(10);
    }

    @Test
    void reachingSeventyFiveAfterTheComputedClasses() {
        int held = 42 + 10;
        int attended = 29 + 10;
        assertThat(AttendanceMath.percentage(held, attended, 0)).isEqualTo(75.0);
        assertThat(AttendanceMath.classesNeededFor75(held, attended, 0)).isZero();
    }

    @Test
    void dutyLeaveCountsAsPresent() {
        assertThat(AttendanceMath.percentage(40, 26, 2)).isEqualTo(70.0);
        assertThat(AttendanceMath.classesNeededFor75(40, 26, 2)).isEqualTo(8);
    }

    @Test
    void roundsHalfUpToOneDecimal() {
        assertThat(AttendanceMath.percentage(36, 30, 0)).isEqualTo(83.3);
        assertThat(AttendanceMath.percentage(30, 22, 0)).isEqualTo(73.3);
        assertThat(AttendanceMath.percentage(8, 7, 0)).isEqualTo(87.5);
        assertThat(AttendanceMath.percentage(3, 2, 0)).isEqualTo(66.7);
    }

    @Test
    void noClassesHeldIsAHundredPercent() {
        assertThat(AttendanceMath.percentage(0, 0, 0)).isEqualTo(100.0);
        assertThat(AttendanceMath.shortage(100.0)).isFalse();
        assertThat(AttendanceMath.classesNeededFor75(0, 0, 0)).isZero();
    }

    @Test
    void notInShortageNeedsNothing() {
        assertThat(AttendanceMath.classesNeededFor75(40, 35, 0)).isZero();
        assertThat(AttendanceMath.shortage(75.0)).isFalse();
        assertThat(AttendanceMath.shortage(74.9)).isTrue();
    }
}
