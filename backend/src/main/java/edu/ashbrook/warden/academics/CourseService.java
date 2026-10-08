package edu.ashbrook.warden.academics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.academics.AcademicDtos.CourseDto;
import edu.ashbrook.warden.academics.AcademicDtos.RosterRow;
import edu.ashbrook.warden.approval.ActionRequest;
import edu.ashbrook.warden.approval.ActionRequestDto;
import edu.ashbrook.warden.approval.ActionRequestRepository;
import edu.ashbrook.warden.approval.ActionTool;
import edu.ashbrook.warden.approval.ApprovalEligibility;
import edu.ashbrook.warden.common.BusinessRuleException;
import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.NotFoundException;
import edu.ashbrook.warden.common.Text;
import edu.ashbrook.warden.user.CurrentUser;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserSummary;

@Service
public class CourseService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final ActionRequestRepository actionRequests;
    private final ApprovalEligibility eligibility;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final Duration approvalTtl;

    public CourseService(CourseRepository courses, EnrollmentRepository enrollments,
                         ActionRequestRepository actionRequests, ApprovalEligibility eligibility,
                         CurrentUser currentUser, Clock clock,
                         @Value("${warden.approvals.ttl}") Duration approvalTtl) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.actionRequests = actionRequests;
        this.eligibility = eligibility;
        this.currentUser = currentUser;
        this.clock = clock;
        this.approvalTtl = approvalTtl;
    }

    /** STUDENT: enrolled courses; FACULTY: courses taught; HOD: the whole department; ADMIN: everything. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<CourseDto> list() {
        User me = currentUser.user();
        List<Course> visible = switch (me.getRole()) {
            case STUDENT -> courses.findByStudent(me.getId());
            case FACULTY -> courses.findByFaculty(me.getId());
            case HOD -> courses.findByDepartment(me.getDepartmentCode());
            case ADMIN -> courses.findAllWithFaculty();
        };
        Map<String, List<Enrollment>> byCourse = visible.isEmpty() ? Map.of()
                : enrollments.findByCourses(visible.stream().map(Course::getCode).toList()).stream()
                        .collect(Collectors.groupingBy(e -> e.getCourse().getCode()));
        return visible.stream().map(c -> {
            List<Enrollment> enrolled = byCourse.getOrDefault(c.getCode(), List.of());
            int shortages = (int) enrolled.stream()
                    .filter(e -> AttendanceMath.shortage(AttendanceMath.percentage(c.getClassesHeld(),
                            e.getClassesAttended(), e.getDutyLeaveCredited())))
                    .count();
            return new CourseDto(c.getCode(), c.getTitle(), c.getDepartmentCode(), c.getCredits(), c.getSemester(),
                    UserSummary.of(c.getFaculty()), c.getClassesHeld(), enrolled.size(), shortages);
        }).toList();
    }

    @PreAuthorize("@courseSecurity.canViewRoster(#code, authentication)")
    @Transactional(readOnly = true)
    public List<RosterRow> roster(String code) {
        Course course = courses.findById(code).orElseThrow(() -> new NotFoundException("There's no course " + code + "."));
        Set<Long> pending = new HashSet<>(actionRequests.findPendingGradeChangeEnrollments(code));
        return enrollments.findRoster(code).stream().map(e -> {
            double pct = AttendanceMath.percentage(course.getClassesHeld(), e.getClassesAttended(), e.getDutyLeaveCredited());
            User s = e.getStudent();
            return new RosterRow(e.getId(), s.getId().toString(), s.getFullName(), s.getRollNo(), e.getClassesAttended(),
                    e.getDutyLeaveCredited(), pct, AttendanceMath.shortage(pct), e.getPublishedGrade(),
                    pending.contains(e.getId()));
        }).toList();
    }

    /** REST path: the roster identifies the student by enrollment id. */
    @PreAuthorize("hasAnyRole('FACULTY','HOD') and @courseSecurity.teaches(#code, authentication)")
    @Transactional
    public ActionRequestDto requestGradeChange(String code, long enrollmentId, Grade newGrade, String reason) {
        Enrollment enrollment = enrollments.findDetailed(enrollmentId)
                .filter(e -> e.getCourse().getCode().equals(code))
                .orElseThrow(() -> new NotFoundException("That student isn't enrolled in " + code + "."));
        return queue(enrollment, newGrade, reason, null);
    }

    /** AI path: the model names the student by roll number. The grade is parsed only after authorization. */
    @PreAuthorize("hasAnyRole('FACULTY','HOD') and @courseSecurity.teaches(#code, authentication)")
    @Transactional
    public ActionRequestDto requestGradeChange(String code, String rollNo, String newGrade, String reason,
                                               UUID conversationId) {
        Enrollment enrollment = enrollments.findByCourseAndRollNo(code, Text.trim(rollNo))
                .orElseThrow(() -> new NotFoundException("No student with roll number " + Text.trim(rollNo)
                        + " is enrolled in " + code + "."));
        return queue(enrollment, Grade.fromSymbol(newGrade), reason, conversationId);
    }

    /** Never changes a grade directly: it always creates a request for a second person to approve. */
    private ActionRequestDto queue(Enrollment enrollment, Grade newGrade, String reason, UUID conversationId) {
        String cleanReason = Text.trim(reason);
        if (!Text.lengthBetween(cleanReason, 15, 500)) {
            throw new FieldValidationException("reason", "Give a reason of 15 to 500 characters.");
        }
        if (newGrade == null) {
            throw new FieldValidationException("newGrade", "Choose the new grade.");
        }
        if (!enrollment.isGradePublished()) {
            throw new BusinessRuleException("Grades can only be changed after they're published.");
        }
        if (enrollment.getGrade() == newGrade) {
            throw new BusinessRuleException("The new grade is the same as the current grade.");
        }
        if (actionRequests.existsPendingGradeChange(String.valueOf(enrollment.getId()))) {
            throw new ConflictException("A change for this student is already pending.");
        }
        User requester = currentUser.user();
        Course course = enrollment.getCourse();
        User student = enrollment.getStudent();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("enrollmentId", String.valueOf(enrollment.getId()));
        args.put("courseCode", course.getCode());
        args.put("rollNo", student.getRollNo());
        args.put("studentName", student.getFullName());
        args.put("oldGrade", enrollment.getGrade().symbol());
        args.put("newGrade", newGrade.symbol());
        args.put("reason", cleanReason);
        String summary = "Change %s's %s grade from %s to %s".formatted(student.getFullName(), course.getCode(),
                enrollment.getGrade(), newGrade);
        ApprovalEligibility.Routing routing = eligibility.routeGradeChange(course, requester);
        Instant now = clock.instant();
        ActionRequest request = new ActionRequest(conversationId, requester, ActionTool.requestGradeChange, args,
                summary, routing.requiredRole(), routing.departmentCode(), now, now.plus(approvalTtl));
        try {
            actionRequests.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A change for this student is already pending.");
        }
        return ActionRequestDto.of(request, false);
    }
}
