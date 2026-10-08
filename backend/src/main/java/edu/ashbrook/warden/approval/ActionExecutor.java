package edu.ashbrook.warden.approval;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import edu.ashbrook.warden.academics.CourseRepository;
import edu.ashbrook.warden.academics.Enrollment;
import edu.ashbrook.warden.academics.EnrollmentRepository;
import edu.ashbrook.warden.academics.Grade;
import edu.ashbrook.warden.academics.GradeChange;
import edu.ashbrook.warden.academics.GradeChangeRepository;
import edu.ashbrook.warden.auth.ActiveUserFilter;
import edu.ashbrook.warden.auth.RefreshTokenRepository;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserRepository;

/**
 * Runs an approved request. Everything is re-validated here because state may have changed since the request was
 * queued; a business failure is a recorded outcome (status FAILED), not an exception.
 */
@Component
class ActionExecutor {

    record Result(boolean ok, String failureReason) {
        static Result done() {
            return new Result(true, null);
        }

        static Result failed(String reason) {
            return new Result(false, reason);
        }
    }

    private final EnrollmentRepository enrollments;
    private final GradeChangeRepository gradeChanges;
    private final CourseRepository courses;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ActiveUserFilter activeUserFilter;
    private final Clock clock;

    ActionExecutor(EnrollmentRepository enrollments, GradeChangeRepository gradeChanges, CourseRepository courses,
                   UserRepository users, RefreshTokenRepository refreshTokens, ActiveUserFilter activeUserFilter,
                   Clock clock) {
        this.enrollments = enrollments;
        this.gradeChanges = gradeChanges;
        this.courses = courses;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.activeUserFilter = activeUserFilter;
        this.clock = clock;
    }

    Result execute(ActionRequest request, User reviewer) {
        return switch (request.getToolName()) {
            case requestGradeChange -> changeGrade(request, reviewer);
            case deactivateUser -> deactivate(request);
        };
    }

    private Result changeGrade(ActionRequest request, User reviewer) {
        Map<String, String> args = request.getArguments();
        User requester = request.getRequestedBy();
        if (!requester.isActive()) {
            return Result.failed("The requester's account is no longer active.");
        }
        Enrollment enrollment = enrollments.findDetailed(Long.valueOf(args.get("enrollmentId"))).orElse(null);
        if (enrollment == null) {
            return Result.failed("The enrollment no longer exists.");
        }
        if (!enrollment.getCourse().getCode().equals(args.get("courseCode"))
                || !enrollment.getCourse().getFaculty().getId().equals(requester.getId())) {
            return Result.failed("The requester no longer teaches this course.");
        }
        Grade oldGrade = Grade.fromSymbol(args.get("oldGrade"));
        Grade newGrade = Grade.fromSymbol(args.get("newGrade"));
        if (!enrollment.isGradePublished() || enrollment.getGrade() != oldGrade) {
            return Result.failed("The grade changed after this request was raised.");
        }
        enrollment.changeGrade(newGrade);
        gradeChanges.save(new GradeChange(enrollment.getId(), oldGrade, newGrade, args.get("reason"),
                requester.getId(), reviewer.getId(), request.getId(), clock.instant()));
        return Result.done();
    }

    private Result deactivate(ActionRequest request) {
        UUID targetId = UUID.fromString(request.getArguments().get("targetUserId"));
        User target = users.findById(targetId).orElse(null);
        if (target == null) {
            return Result.failed("That account no longer exists.");
        }
        if (target.getId().equals(request.getRequestedBy().getId())) {
            return Result.failed("An administrator can't deactivate their own account.");
        }
        if (!target.isActive()) {
            return Result.failed("This account is already inactive.");
        }
        if (target.getRole() == Role.ADMIN) {
            // Lock every active admin row so two cross-approved deactivations can't both pass this check.
            List<User> activeAdmins = users.lockActiveByRole(Role.ADMIN);
            if (activeAdmins.size() <= 1) {
                return Result.failed("They're the last active administrator.");
            }
        }
        if (courses.existsByFacultyId(targetId)) {
            return Result.failed("Reassign their courses first.");
        }
        if (users.existsByMentorIdAndActiveTrue(targetId)) {
            return Result.failed("Reassign their mentees first.");
        }
        Instant now = clock.instant();
        target.deactivate();
        refreshTokens.revokeAllForUser(targetId, now);
        activeUserFilter.evict(targetId);
        // Evict again once committed, so a request racing this transaction can't re-cache the old "active" value.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                activeUserFilter.evict(targetId);
            }
        });
        return Result.done();
    }
}
