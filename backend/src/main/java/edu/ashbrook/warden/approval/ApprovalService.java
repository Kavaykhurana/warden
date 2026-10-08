package edu.ashbrook.warden.approval;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditType;
import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.ForbiddenException;
import edu.ashbrook.warden.common.NotFoundException;
import edu.ashbrook.warden.common.Text;
import edu.ashbrook.warden.user.CurrentUser;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.User;

/** Human review of queued actions. Approval is deliberately not an AI tool: a person clicks Approve. */
@Service
public class ApprovalService {

    public enum Scope { REVIEW, MINE }

    private final ActionRequestRepository requests;
    private final ApprovalEligibility eligibility;
    private final ActionExecutor executor;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final Clock clock;

    public ApprovalService(ActionRequestRepository requests, ApprovalEligibility eligibility, ActionExecutor executor,
                           CurrentUser currentUser, AuditService audit, Clock clock) {
        this.requests = requests;
        this.eligibility = eligibility;
        this.executor = executor;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('FACULTY','HOD','ADMIN')")
    @Transactional(readOnly = true)
    public List<ActionRequestDto> list(Scope scope, boolean pendingOnly) {
        User me = currentUser.user();
        Set<ActionStatus> statuses = pendingOnly ? EnumSet.of(ActionStatus.PENDING) : EnumSet.allOf(ActionStatus.class);
        List<ActionRequest> found = switch (scope) {
            case MINE -> requests.findRequestedBy(me.getId(), statuses);
            case REVIEW -> me.getRole() == Role.HOD || me.getRole() == Role.ADMIN
                    ? requests.findByRequiredRole(me.getRole(), statuses).stream()
                        .filter(r -> eligibility.isReviewerFor(r, me))
                        .toList()
                    : List.of();
        };
        return found.stream().map(r -> ActionRequestDto.of(r, eligibility.canReview(r, me))).toList();
    }

    @PreAuthorize("hasAnyRole('HOD','ADMIN')")
    @Transactional
    public ActionRequestDto approve(UUID id, String note) {
        String cleanNote = Text.blankToNull(note);
        if (cleanNote != null && cleanNote.length() > 500) {
            throw new FieldValidationException("note", "Keep the note to 500 characters or fewer.");
        }
        User reviewer = currentUser.user();
        ActionRequest request = lockReviewable(id, reviewer);
        ActionExecutor.Result result = executor.execute(request, reviewer);
        Instant now = clock.instant();
        if (result.ok()) {
            request.executed(reviewer, cleanNote, now);
        } else {
            request.failed(reviewer, cleanNote, result.failureReason(), now);
        }
        audit.appendAfterCommit(reviewer.getId(), result.ok() ? AuditType.ACTION_EXECUTED : AuditType.ACTION_FAILED,
                request.getToolName().name(), result.ok()
                        ? Map.of("actionRequestId", id.toString())
                        : Map.of("actionRequestId", id.toString(), "failureReason", result.failureReason()));
        return ActionRequestDto.of(request, false);
    }

    @PreAuthorize("hasAnyRole('HOD','ADMIN')")
    @Transactional
    public ActionRequestDto reject(UUID id, String note) {
        String cleanNote = Text.trim(note);
        if (!Text.lengthBetween(cleanNote, 5, 500)) {
            throw new FieldValidationException("note", "Give a reason of 5 to 500 characters.");
        }
        User reviewer = currentUser.user();
        ActionRequest request = lockReviewable(id, reviewer);
        request.rejected(reviewer, cleanNote, clock.instant());
        audit.appendAfterCommit(reviewer.getId(), AuditType.ACTION_REJECTED, request.getToolName().name(),
                Map.of("actionRequestId", id.toString()));
        return ActionRequestDto.of(request, false);
    }

    /** Row lock first, so of two simultaneous decisions the second sees the first's result and gets a 409. */
    private ActionRequest lockReviewable(UUID id, User reviewer) {
        ActionRequest request = requests.findForUpdate(id)
                .orElseThrow(() -> new NotFoundException("That approval request doesn't exist."));
        if (request.getStatus() != ActionStatus.PENDING) {
            throw new ConflictException("This request was already decided.");
        }
        if (request.isExpired(clock.instant())) {
            throw new ConflictException("This request has expired.");
        }
        if (!eligibility.canReview(request, reviewer)) {
            throw new ForbiddenException("You're not allowed to review this request.");
        }
        return request;
    }
}
