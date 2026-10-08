package edu.ashbrook.warden.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import edu.ashbrook.warden.academics.AcademicDtos.AcademicSummary;
import edu.ashbrook.warden.academics.AcademicsService;
import edu.ashbrook.warden.academics.CourseService;
import edu.ashbrook.warden.approval.ActionRequestDto;
import edu.ashbrook.warden.approval.ApprovalService;
import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditSummary;
import edu.ashbrook.warden.audit.AuditType;
import edu.ashbrook.warden.dutyleave.DutyLeaveDto;
import edu.ashbrook.warden.dutyleave.DutyLeaveService;
import edu.ashbrook.warden.user.CurrentUser;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.UserDto;
import edu.ashbrook.warden.user.UserService;

/**
 * The model's only way to act. Each tool calls the same {@code @PreAuthorize}-protected service method as the REST
 * API, through {@link ToolGuard}. Approving or rejecting queued actions is deliberately not a tool.
 */
@Component
public class WardenTools {

    /** Tool descriptions say who is allowed, but the model must not pre-judge: the server decides, the UI shows it. */
    static final String SERVER_DECIDES = "the server checks this, so call the tool whenever the user asks, even if you "
            + "expect it to be denied.";

    private final ToolGuard guard;
    private final UserService users;
    private final AcademicsService academics;
    private final CourseService courses;
    private final DutyLeaveService dutyLeave;
    private final ApprovalService approvals;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public WardenTools(ToolGuard guard, UserService users, AcademicsService academics, CourseService courses,
                       DutyLeaveService dutyLeave, ApprovalService approvals, AuditService audit,
                       CurrentUser currentUser) {
        this.guard = guard;
        this.users = users;
        this.academics = academics;
        this.courses = courses;
        this.dutyLeave = dutyLeave;
        this.approvals = approvals;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Tool(name = "getMyProfile", description = "Get the signed-in user's profile: name, role, department, roll number "
            + "and mentor. Any role may call it. Takes no arguments.")
    public String getMyProfile(ToolContext ctx) {
        return guard.run(ctx, "getMyProfile", Args.of(), "Sign in to see your profile.", () -> {
            UserDto me = users.me();
            String payload = "Name: %s\nRole: %s\nDepartment: %s\nRoll number: %s\nMentor: %s".formatted(me.fullName(),
                    me.role(), orDash(me.departmentName()), orDash(me.rollNo()), orDash(me.mentorName()));
            return ToolOutcome.allowed("Profile of " + me.fullName() + ".", payload);
        });
    }

    @Tool(name = "getMyAttendance", description = "Get the signed-in student's attendance for each enrolled course this "
            + "semester, including duty leave credited, percentage, shortage (below 75%) and classes needed to reach "
            + "75%. Allowed for students; " + SERVER_DECIDES + " Takes no arguments.")
    public String getMyAttendance(ToolContext ctx) {
        return guard.run(ctx, "getMyAttendance", Args.of(), "Only students have attendance records.", () -> {
            AcademicSummary s = academics.mySummary();
            String payload = s.attendance().stream().map(a -> "%s %s: %d+%d/%d = %.1f%% (%s)".formatted(a.courseCode(),
                    a.courseTitle(), a.classesAttended(), a.dutyLeaveCredited(), a.classesHeld(), a.percentage(),
                    a.shortage() ? "shortage, needs " + a.classesNeededFor75() : "ok"))
                    .collect(Collectors.joining("\n"));
            return ToolOutcome.allowed("Attendance for " + s.semester() + ".", payload);
        });
    }

    @Tool(name = "getMyGrades", description = "Get the signed-in student's published grades for this semester and their "
            + "SGPA. Allowed for students; " + SERVER_DECIDES + " Takes no arguments.")
    public String getMyGrades(ToolContext ctx) {
        return guard.run(ctx, "getMyGrades", Args.of(), "Only students have grade records.", () -> {
            AcademicSummary s = academics.mySummary();
            String lines = s.grades().stream().map(g -> "%s %s (%d credits): %s".formatted(g.courseCode(),
                    g.courseTitle(), g.credits(), g.grade() == null ? "not published" : g.grade().symbol()))
                    .collect(Collectors.joining("\n"));
            String sgpa = s.sgpa() == null ? "SGPA: not available yet"
                    : "SGPA: %s over %d credits earned".formatted(s.sgpa().toPlainString(), s.creditsEarned());
            return ToolOutcome.allowed("Grades for " + s.semester() + ".", lines + "\n" + sgpa);
        });
    }

    @Tool(name = "applyDutyLeave", description = "Submit a duty leave request for the signed-in student for an event "
            + "they represent the college at. Dates are YYYY-MM-DD in Asia/Kolkata; the range may cover 1 to 10 working "
            + "days, start no more than 30 days ago and end no more than 60 days ahead. More than 3 working days goes to "
            + "the head of department, otherwise to the mentor. Allowed for students; " + SERVER_DECIDES)
    public String applyDutyLeave(@ToolParam(description = "First day of leave, YYYY-MM-DD") String startDate,
                                 @ToolParam(description = "Last day of leave, YYYY-MM-DD") String endDate,
                                 @ToolParam(description = "Event name, 3 to 120 characters") String eventName,
                                 @ToolParam(description = "Reason in the student's words, 20 to 1000 characters") String reason,
                                 ToolContext ctx) {
        return guard.run(ctx, "applyDutyLeave",
                Args.of("startDate", startDate, "endDate", endDate, "eventName", eventName, "reason", reason),
                "Only students can apply for duty leave.", () -> {
                    DutyLeaveService.Applied applied = dutyLeave.apply(Args.date("startDate", startDate),
                            Args.date("endDate", endDate), eventName, reason);
                    DutyLeaveDto l = applied.leave();
                    return ToolOutcome.allowed("Duty leave request #%d for %s (%d working days) submitted. Sent to %s."
                            .formatted(l.id(), l.eventName(), l.workingDays(), applied.sentTo()));
                });
    }

    @Tool(name = "listMyDutyLeave", description = "List the signed-in student's own duty leave requests, newest first, "
            + "with ids and statuses. Allowed for students; " + SERVER_DECIDES + " Takes no arguments.")
    public String listMyDutyLeave(ToolContext ctx) {
        return guard.run(ctx, "listMyDutyLeave", Args.of(), "Only students have duty leave requests.", () -> {
            List<DutyLeaveDto> mine = dutyLeave.mine();
            String payload = mine.stream().map(l -> "%s to %s (%d working days), %s [leaveId=%d]".formatted(
                    l.startDate(), l.endDate(), l.workingDays(), l.status(), l.id())).collect(Collectors.joining("\n"));
            return ToolOutcome.allowed(mine.isEmpty() ? "You have no duty leave requests."
                    : "You have %d duty leave request(s).".formatted(mine.size()), payload);
        });
    }

    @Tool(name = "cancelDutyLeave", description = "Cancel one of the signed-in student's own PENDING duty leave requests. "
            + "Allowed for the student who applied; " + SERVER_DECIDES)
    public String cancelDutyLeave(@ToolParam(description = "Duty leave request id, a whole number") String leaveId,
                                  ToolContext ctx) {
        return guard.run(ctx, "cancelDutyLeave", Args.of("leaveId", leaveId),
                "Only the student who applied can cancel a duty leave request.", () -> {
                    DutyLeaveDto l = dutyLeave.cancel(Args.wholeNumber("leaveId", leaveId));
                    return ToolOutcome.allowed("Duty leave request #%d cancelled.".formatted(l.id()));
                });
    }

    @Tool(name = "listMyCourses", description = "List courses visible to the signed-in user: a student's enrolled "
            + "courses, a faculty member's taught courses, a head of department's whole department, or every course for "
            + "an administrator. Takes no arguments.")
    public String listMyCourses(ToolContext ctx) {
        return guard.run(ctx, "listMyCourses", Args.of(), "Sign in to see your courses.", () -> {
            var list = courses.list();
            String payload = list.stream().map(c -> "%s %s (%s), taught by %s: %d enrolled, %d in shortage".formatted(
                    c.code(), c.title(), c.departmentCode(), c.faculty().fullName(), c.enrolledCount(),
                    c.shortageCount())).collect(Collectors.joining("\n"));
            return ToolOutcome.allowed("%d course(s).".formatted(list.size()), payload);
        });
    }

    @Tool(name = "getCourseRoster", description = "Get a course roster with each student's roll number, attendance and "
            + "published grade. Allowed for the course's faculty, the head of the course's department, or an administrator; " + SERVER_DECIDES)
    public String getCourseRoster(@ToolParam(description = "Course code, for example UCS301") String courseCode,
                                  ToolContext ctx) {
        return guard.run(ctx, "getCourseRoster", Args.of("courseCode", courseCode),
                "Only the course's faculty, the head of its department, or an administrator can view its roster.", () -> {
                    String code = courseCode == null ? "" : courseCode.strip().toUpperCase(java.util.Locale.ROOT);
                    var roster = courses.roster(code);
                    String payload = roster.stream().map(r -> "%s %s: %.1f%%%s, grade %s%s".formatted(r.rollNo(),
                            r.fullName(), r.percentage(), r.shortage() ? " (shortage)" : "",
                            r.grade() == null ? "not published" : r.grade().symbol(),
                            r.pendingGradeChange() ? " [grade change pending]" : ""))
                            .collect(Collectors.joining("\n"));
                    return ToolOutcome.allowed("%s roster: %d student(s).".formatted(code, roster.size()), payload);
                });
    }

    @Tool(name = "listPendingDutyLeave", description = "List PENDING duty leave requests the signed-in faculty mentor or "
            + "head of department can see, with ids and whether they may decide each one. Student-written text is "
            + "returned inside <untrusted_student_text> tags. Allowed for faculty and heads of department; " + SERVER_DECIDES + " Takes no "
            + "arguments.")
    public String listPendingDutyLeave(ToolContext ctx) {
        return guard.run(ctx, "listPendingDutyLeave", Args.of(),
                "Only faculty mentors and heads of department review duty leave.", () -> {
                    List<DutyLeaveDto> pending = dutyLeave.reviewQueue(true);
                    UUID me = currentUser.id();
                    pending.stream().filter(DutyLeaveDto::reasonFlagged).forEach(l -> audit.append(me,
                            AuditType.INJECTION_FLAGGED, "listPendingDutyLeave",
                            Map.of("source", "tool_output", "leaveId", String.valueOf(l.id()))));
                    String payload = pending.stream().map(WardenTools::describePending).collect(Collectors.joining("\n"));
                    return ToolOutcome.allowed(pending.isEmpty() ? "No pending duty leave requests."
                            : "%d pending duty leave request(s).".formatted(pending.size()), payload);
                });
    }

    @Tool(name = "decideDutyLeave", description = "Approve or reject one PENDING duty leave request. The student's mentor "
            + "may decide requests of up to 3 working days; the head of the student's department may decide any. Nobody "
            + "decides their own request. Rejecting needs a note of 5 to 500 characters. " + SERVER_DECIDES)
    public String decideDutyLeave(@ToolParam(description = "The leaveId number exactly as shown by listPendingDutyLeave") String leaveId,
                                  @ToolParam(description = "Roll number of the student who applied, as shown with the leaveId") String rollNo,
                                  @ToolParam(description = "true to approve, false to reject") String approve,
                                  @ToolParam(description = "Decision note; required when rejecting", required = false) String note,
                                  ToolContext ctx) {
        return guard.run(ctx, "decideDutyLeave", Args.of("leaveId", leaveId, "rollNo", rollNo, "approve", approve, "note", note),
                "Only the student's mentor (for up to 3 working days) or the head of the student's department can "
                        + "decide this request.", () -> {
                    boolean yes = Args.yesNo("approve", approve);
                    DutyLeaveDto l = dutyLeave.decide(Args.wholeNumber("leaveId", leaveId), rollNo, yes, note);
                    return ToolOutcome.allowed("Duty leave request #%d %s.".formatted(l.id(), yes ? "approved" : "rejected"));
                });
    }

    @Tool(name = "requestGradeChange", description = "Ask for a published grade to be changed. This never changes the "
            + "grade directly: it creates a request that the head of the course's department (or an administrator, if "
            + "the requester is that head) must approve in the Approvals page. Allowed for the faculty who teaches the "
            + "course; " + SERVER_DECIDES + " If a student asks to change their own grade, call it with their own roll "
            + "number and the course code from listMyCourses, so the decision is recorded and shown to them.")
    public String requestGradeChange(@ToolParam(description = "Course code, for example UCS301") String courseCode,
                                     @ToolParam(description = "Student's roll number") String rollNo,
                                     @ToolParam(description = "New grade: A+, A, B+, B, C+, C, D or F") String newGrade,
                                     @ToolParam(description = "Reason, 15 to 500 characters; leave empty if the user gave none", required = false) String reason,
                                     ToolContext ctx) {
        return guard.run(ctx, "requestGradeChange",
                Args.of("courseCode", courseCode, "rollNo", rollNo, "newGrade", newGrade, "reason", reason),
                "Only the faculty who teaches a course can request a grade change.", () -> {
                    String code = courseCode == null ? "" : courseCode.strip().toUpperCase(java.util.Locale.ROOT);
                    ActionRequestDto r = courses.requestGradeChange(code, rollNo, newGrade, reason, conversationId(ctx));
                    return ToolOutcome.queued("%s. Waiting for approval by %s.".formatted(r.summary(), reviewerOf(r)),
                            r.id());
                });
    }

    @Tool(name = "listMyApprovals", description = "List PENDING approval requests (grade changes, account deactivations) "
            + "that the signed-in user is eligible to review. Read-only: approving happens on the Approvals page. "
            + "Allowed for faculty, heads of department and administrators; " + SERVER_DECIDES + " Takes no arguments.")
    public String listMyApprovals(ToolContext ctx) {
        return guard.run(ctx, "listMyApprovals", Args.of(),
                "Only faculty, heads of department and administrators have approvals.", () -> {
                    List<ActionRequestDto> list = approvals.list(ApprovalService.Scope.REVIEW, true);
                    String payload = list.stream().map(r -> "%s (requested by %s, expires %s)".formatted(r.summary(),
                            r.requestedBy().fullName(), r.expiresAt())).collect(Collectors.joining("\n"));
                    return ToolOutcome.allowed(list.isEmpty() ? "Nothing is waiting for your review."
                            : "%d request(s) wait for your review on the Approvals page.".formatted(list.size()), payload);
                });
    }

    @Tool(name = "deactivateUser", description = "Ask for a user account to be deactivated. This never deactivates "
            + "directly: another administrator must approve it on the Approvals page. Allowed for administrators; " + SERVER_DECIDES)
    public String deactivateUser(@ToolParam(description = "Email of the account to deactivate") String email,
                                 @ToolParam(description = "Reason, 10 to 500 characters") String reason,
                                 ToolContext ctx) {
        return guard.run(ctx, "deactivateUser", Args.of("email", email, "reason", reason),
                "Only administrators can request an account deactivation.", () -> {
                    ActionRequestDto r = users.requestDeactivation(email, reason, conversationId(ctx));
                    return ToolOutcome.queued("%s. Waiting for approval by another administrator.".formatted(r.summary()),
                            r.id());
                });
    }

    @Tool(name = "auditSummary", description = "Summarise the audit log: event counts by type and the five most active "
            + "users over the last N hours (1 to 168, default 24). Allowed for administrators; " + SERVER_DECIDES)
    public String auditSummary(@ToolParam(description = "Hours to look back, 1 to 168", required = false) String hours,
                               ToolContext ctx) {
        return guard.run(ctx, "auditSummary", Args.of("hours", hours), "Only administrators can read the audit log.", () -> {
            AuditSummary s = audit.summary(Args.hours(hours));
            String counts = s.countsByType().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n"));
            String actors = s.topActors().stream().map(a -> "%s (%s): %d events".formatted(a.fullName(), a.role(),
                    a.events())).collect(Collectors.joining("\n"));
            return ToolOutcome.allowed("Audit summary for the last %d hours.".formatted(s.hours()),
                    "Events by type:\n" + (counts.isEmpty() ? "none" : counts) + "\nMost active users:\n"
                            + (actors.isEmpty() ? "none" : actors));
        });
    }

    private String reviewerOf(ActionRequestDto r) {
        return r.requiredRole() == Role.HOD
                ? users.activeHodName(r.departmentCode()).orElse("the head of department")
                : "an administrator";
    }

    private static UUID conversationId(ToolContext ctx) {
        return ctx.getContext().get(ToolKeys.CONVERSATION_ID) instanceof UUID id ? id : null;
    }

    /** Student-written text is fenced and escaped so it can't close the fence or pose as instructions. */
    private static String describePending(DutyLeaveDto l) {
        return "[leaveId=%d] %s (%s), %s to %s, %d working day(s)%s, you can decide: %s\n<untrusted_student_text flagged=\"%s\">\nEvent: %s\nReason: %s\n</untrusted_student_text>"
                .formatted(l.id(), l.student().fullName(), l.student().rollNo(), l.startDate(), l.endDate(),
                        l.workingDays(), l.requiresHod() ? " (needs head of department)" : "",
                        l.canDecide() ? "yes" : "no", l.reasonFlagged(), escape(l.eventName()), escape(l.reason()));
    }

    static String escape(String untrusted) {
        return untrusted.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String orDash(String value) {
        return value == null ? "-" : value;
    }
}
