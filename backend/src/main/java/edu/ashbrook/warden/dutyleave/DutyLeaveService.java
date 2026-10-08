package edu.ashbrook.warden.dutyleave;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.academics.Enrollment;
import edu.ashbrook.warden.academics.EnrollmentRepository;
import edu.ashbrook.warden.ai.PromptGuard;
import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditType;
import edu.ashbrook.warden.common.BusinessRuleException;
import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.NotFoundException;
import edu.ashbrook.warden.common.Text;
import edu.ashbrook.warden.user.CurrentUser;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserRepository;

@Service
public class DutyLeaveService {

    public record Applied(DutyLeaveDto leave, String sentTo) {
    }

    private final DutyLeaveRepository leaves;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final DutyLeaveSecurity security;
    private final PromptGuard promptGuard;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final Clock clock;

    public DutyLeaveService(DutyLeaveRepository leaves, EnrollmentRepository enrollments, UserRepository users,
                            DutyLeaveSecurity security, PromptGuard promptGuard, CurrentUser currentUser,
                            AuditService audit, Clock clock) {
        this.leaves = leaves;
        this.enrollments = enrollments;
        this.users = users;
        this.security = security;
        this.promptGuard = promptGuard;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public Applied apply(LocalDate startDate, LocalDate endDate, String eventName, String reason) {
        String event = Text.trim(eventName);
        String why = Text.trim(reason);
        Map<String, String> errors = new LinkedHashMap<>();
        if (startDate == null) {
            errors.put("startDate", "Choose a start date.");
        }
        if (endDate == null) {
            errors.put("endDate", "Choose an end date.");
        } else if (startDate != null && endDate.isBefore(startDate)) {
            errors.put("endDate", "The end date can't be before the start date.");
        }
        if (!Text.lengthBetween(event, 3, 120)) {
            errors.put("eventName", "Name the event in 3 to 120 characters.");
        }
        if (!Text.lengthBetween(why, 20, 1000)) {
            errors.put("reason", "Give a reason of 20 to 1000 characters.");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        LocalDate today = LocalDate.now(clock);
        if (startDate.isBefore(today.minusDays(30))) {
            throw new BusinessRuleException("Duty leave can start at most 30 days in the past.");
        }
        if (endDate.isAfter(today.plusDays(60))) {
            throw new BusinessRuleException("Duty leave can end at most 60 days from today.");
        }
        int workingDays = WorkingDays.between(startDate, endDate);
        if (workingDays < 1) {
            throw new BusinessRuleException("Those dates don't include a working day.");
        }
        if (workingDays > 10) {
            throw new BusinessRuleException("Duty leave can cover at most 10 working days.");
        }
        User student = currentUser.user();
        // The reason is data, not a command: a suspicious one is flagged for the reviewer but still accepted.
        boolean flagged = promptGuard.assess(why).flagged();
        DutyLeaveRequest leave = new DutyLeaveRequest(student, startDate, endDate, workingDays, event, why, flagged,
                clock.instant());
        try {
            leaves.saveAndFlush(leave);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("no_overlapping_duty_leave")) {
                throw new ConflictException("This overlaps a duty leave request you already have.");
            }
            throw e;
        }
        String sentTo = leave.isRequiresHod()
                ? users.findActiveHod(student.getDepartmentCode()).map(User::getFullName).orElse("your head of department")
                : student.getMentor().getFullName();
        return new Applied(DutyLeaveDto.of(leave, false, true), sentTo);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public List<DutyLeaveDto> mine() {
        return leaves.findByStudent(currentUser.id()).stream()
                .map(l -> DutyLeaveDto.of(l, false, l.getStatus() == LeaveStatus.PENDING))
                .toList();
    }

    @PreAuthorize("hasRole('STUDENT') and @dutyLeaveSecurity.owns(#id, authentication)")
    @Transactional
    public DutyLeaveDto cancel(Long id) {
        DutyLeaveRequest leave = leaves.findForUpdate(id).orElseThrow(DutyLeaveService::notFound);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("Only pending requests can be cancelled.");
        }
        leave.cancel();
        return DutyLeaveDto.of(leave, false, false);
    }

    /** FACULTY: requests from their mentees. HOD: requests from students of their department. */
    @PreAuthorize("hasAnyRole('FACULTY','HOD')")
    @Transactional(readOnly = true)
    public List<DutyLeaveDto> reviewQueue(boolean pendingOnly) {
        User me = currentUser.user();
        Set<LeaveStatus> statuses = pendingOnly ? EnumSet.of(LeaveStatus.PENDING) : EnumSet.allOf(LeaveStatus.class);
        List<DutyLeaveRequest> found = me.getRole() == Role.HOD
                ? leaves.findForDepartment(me.getDepartmentCode(), statuses)
                : leaves.findForMentor(me.getId(), statuses);
        return found.stream()
                .map(l -> DutyLeaveDto.of(l, l.getStatus() == LeaveStatus.PENDING && security.canDecide(l, me), false))
                .toList();
    }

    @PreAuthorize("hasAnyRole('FACULTY','HOD') and @dutyLeaveSecurity.canDecide(#id, authentication)")
    @Transactional
    public DutyLeaveDto decide(Long id, boolean approve, String note) {
        String cleanNote = Text.blankToNull(note);
        if (!approve && (cleanNote == null || !Text.lengthBetween(cleanNote, 5, 500))) {
            throw new FieldValidationException("note", "Give a reason of 5 to 500 characters.");
        }
        if (cleanNote != null && cleanNote.length() > 500) {
            throw new FieldValidationException("note", "Keep the note to 500 characters or fewer.");
        }
        DutyLeaveRequest leave = leaves.findForUpdate(id).orElseThrow(DutyLeaveService::notFound);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("This request was already decided.");
        }
        User decider = currentUser.user();
        List<String> credited = new ArrayList<>();
        if (approve) {
            for (Enrollment enrollment : enrollments.findByStudent(leave.getStudent().getId())) {
                int credit = enrollment.creditDutyLeave(leave.getWorkingDays());
                credited.add(enrollment.getCourse().getCode() + ":" + credit);
            }
        }
        leave.decide(approve, decider, cleanNote, clock.instant());
        audit.appendAfterCommit(decider.getId(), AuditType.DUTY_LEAVE_DECIDED, null, Map.of(
                "leaveId", String.valueOf(id),
                "approve", String.valueOf(approve),
                "workingDays", String.valueOf(leave.getWorkingDays()),
                "creditedCourses", String.join(",", credited)));
        return DutyLeaveDto.of(leave, false, false);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("That duty leave request doesn't exist.");
    }
}
