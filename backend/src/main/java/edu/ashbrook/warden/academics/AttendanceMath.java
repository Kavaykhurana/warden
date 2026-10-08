package edu.ashbrook.warden.academics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Attendance and SGPA arithmetic from the contract. Pure functions, exact decimal rounding. */
public final class AttendanceMath {

    private AttendanceMath() {
    }

    /** {@code (attended + credited) / held × 100}, one decimal HALF_UP; 100.0 when no classes were held. */
    public static double percentage(int held, int attended, int credited) {
        if (held == 0) {
            return 100.0;
        }
        return BigDecimal.valueOf(100L * (attended + credited))
                .divide(BigDecimal.valueOf(held), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static boolean shortage(double percentage) {
        return percentage < 75;
    }

    /**
     * Consecutive classes to attend to reach 75%: {@code ceil((0.75·held − present) / 0.25)}, which is exactly
     * {@code 3·held − 4·present} in integers. 0 when not in shortage.
     */
    public static int classesNeededFor75(int held, int attended, int credited) {
        if (!shortage(percentage(held, attended, credited))) {
            return 0;
        }
        return Math.max(0, 3 * held - 4 * (attended + credited));
    }

    public record GradedCourse(int credits, Grade grade) {
    }

    /** Σ(credits × points) / Σ credits over published grades, two decimals HALF_UP; null when nothing is published. */
    public static BigDecimal sgpa(List<GradedCourse> published) {
        int credits = published.stream().mapToInt(GradedCourse::credits).sum();
        if (credits == 0) {
            return null;
        }
        int weighted = published.stream().mapToInt(g -> g.credits() * g.grade().points()).sum();
        return BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(credits), 2, RoundingMode.HALF_UP);
    }

    /** Credits of published grades other than F. */
    public static int creditsEarned(List<GradedCourse> published) {
        return published.stream().filter(g -> g.grade() != Grade.F).mapToInt(GradedCourse::credits).sum();
    }
}
