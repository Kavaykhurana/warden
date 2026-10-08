package edu.ashbrook.warden.academics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.ashbrook.warden.academics.AttendanceMath.GradedCourse;

class SgpaTest {

    private static final List<GradedCourse> AARAV = List.of(
            new GradedCourse(4, Grade.B_PLUS), new GradedCourse(4, Grade.A),
            new GradedCourse(3, Grade.B), new GradedCourse(3, Grade.A));

    @Test
    void aaravsSgpaAndCredits() {
        assertThat(AttendanceMath.sgpa(AARAV)).isEqualByComparingTo(new BigDecimal("8.29"));
        assertThat(AttendanceMath.sgpa(AARAV).scale()).isEqualTo(2);
        assertThat(AttendanceMath.creditsEarned(AARAV)).isEqualTo(14);
    }

    @Test
    void failCountsInSgpaButEarnsNoCredits() {
        List<GradedCourse> withF = List.of(new GradedCourse(4, Grade.A), new GradedCourse(2, Grade.F));
        assertThat(AttendanceMath.sgpa(withF)).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(AttendanceMath.creditsEarned(withF)).isEqualTo(4);
    }

    @Test
    void nothingPublishedMeansNoSgpa() {
        assertThat(AttendanceMath.sgpa(List.of())).isNull();
        assertThat(AttendanceMath.creditsEarned(List.of())).isZero();
    }
}
