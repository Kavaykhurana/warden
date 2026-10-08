package edu.ashbrook.warden.approval;

import java.time.Clock;

import org.springframework.stereotype.Component;

import edu.ashbrook.warden.academics.Course;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.User;

/**
 * Separation of duties in one place. The same functions compute {@code canReview} in every DTO, filter the
 * {@code scope=review} list, and guard approve/reject — so the UI and the server can never disagree.
 */
@Component
public class ApprovalEligibility {

    public record Routing(Role requiredRole, String departmentCode) {
    }

    private final Clock clock;

    public ApprovalEligibility(Clock clock) {
        this.clock = clock;
    }

    /** Would this user be an eligible reviewer, ignoring status and expiry? Never the requester. */
    public boolean isReviewerFor(ActionRequest request, User user) {
        if (user == null || !user.isActive() || user.getId().equals(request.getRequestedBy().getId())) {
            return false;
        }
        return switch (request.getRequiredRole()) {
            case HOD -> user.isHodOf(request.getDepartmentCode());
            case ADMIN -> user.getRole() == Role.ADMIN;
            default -> false;
        };
    }

    public boolean canReview(ActionRequest request, User user) {
        return request.getStatus() == ActionStatus.PENDING
                && !request.isExpired(clock.instant())
                && isReviewerFor(request, user);
    }

    /** A grade change goes to the HOD of the course's department — unless the requester is that HOD, then to an ADMIN. */
    public Routing routeGradeChange(Course course, User requester) {
        return requester.isHodOf(course.getDepartmentCode())
                ? new Routing(Role.ADMIN, null)
                : new Routing(Role.HOD, course.getDepartmentCode());
    }

    public Routing routeDeactivation() {
        return new Routing(Role.ADMIN, null);
    }
}
