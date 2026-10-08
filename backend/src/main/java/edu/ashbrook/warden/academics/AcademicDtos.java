package edu.ashbrook.warden.academics;

import java.math.BigDecimal;
import java.util.List;

import edu.ashbrook.warden.user.UserSummary;

/** Contract types for the academics and course endpoints. */
public final class AcademicDtos {

    private AcademicDtos() {
    }

    public record AttendanceRow(String courseCode, String courseTitle, int classesHeld, int classesAttended,
                                int dutyLeaveCredited, double percentage, boolean shortage, int classesNeededFor75) {

        static AttendanceRow of(Enrollment e) {
            Course c = e.getCourse();
            int held = c.getClassesHeld();
            double pct = AttendanceMath.percentage(held, e.getClassesAttended(), e.getDutyLeaveCredited());
            return new AttendanceRow(c.getCode(), c.getTitle(), held, e.getClassesAttended(), e.getDutyLeaveCredited(),
                    pct, AttendanceMath.shortage(pct),
                    AttendanceMath.classesNeededFor75(held, e.getClassesAttended(), e.getDutyLeaveCredited()));
        }
    }

    public record GradeRow(String courseCode, String courseTitle, int credits, Grade grade, Integer gradePoint) {
    }

    public record AcademicSummary(String semester, List<AttendanceRow> attendance, List<GradeRow> grades,
                                  BigDecimal sgpa, int creditsEarned) {
    }

    public record CourseDto(String code, String title, String departmentCode, int credits, String semester,
                            UserSummary faculty, int classesHeld, int enrolledCount, int shortageCount) {
    }

    public record RosterRow(long enrollmentId, String studentId, String fullName, String rollNo, int classesAttended,
                            int dutyLeaveCredited, double percentage, boolean shortage, Grade grade,
                            boolean pendingGradeChange) {
    }

    public record GradeChangeRequest(Long enrollmentId, Grade newGrade, String reason) {
    }
}
