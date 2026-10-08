package edu.ashbrook.warden.academics;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.academics.AcademicDtos.AcademicSummary;
import edu.ashbrook.warden.academics.AcademicDtos.AttendanceRow;
import edu.ashbrook.warden.academics.AcademicDtos.GradeRow;
import edu.ashbrook.warden.user.CurrentUser;

@Service
public class AcademicsService {

    private final EnrollmentRepository enrollments;
    private final CurrentUser currentUser;
    private final String semester;

    public AcademicsService(EnrollmentRepository enrollments, CurrentUser currentUser,
                            @Value("${warden.semester}") String semester) {
        this.enrollments = enrollments;
        this.currentUser = currentUser;
        this.semester = semester;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public AcademicSummary mySummary() {
        List<Enrollment> mine = enrollments.findByStudent(currentUser.id());
        List<AttendanceRow> attendance = mine.stream().map(AttendanceRow::of).toList();
        List<GradeRow> grades = mine.stream().map(e -> {
            Grade grade = e.getPublishedGrade();
            return new GradeRow(e.getCourse().getCode(), e.getCourse().getTitle(), e.getCourse().getCredits(), grade,
                    grade == null ? null : grade.points());
        }).toList();
        List<AttendanceMath.GradedCourse> published = grades.stream()
                .filter(g -> g.grade() != null)
                .map(g -> new AttendanceMath.GradedCourse(g.credits(), g.grade()))
                .toList();
        return new AcademicSummary(semester, attendance, grades, AttendanceMath.sgpa(published),
                AttendanceMath.creditsEarned(published));
    }
}
