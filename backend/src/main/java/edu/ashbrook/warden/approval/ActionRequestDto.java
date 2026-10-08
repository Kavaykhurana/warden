package edu.ashbrook.warden.approval;

import java.time.OffsetDateTime;
import java.util.Map;

import edu.ashbrook.warden.config.ClockConfig;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.UserSummary;

/** Contract type {@code ActionRequest}. */
public record ActionRequestDto(String id, ActionTool toolName, String summary, Map<String, String> arguments,
                               Role requiredRole, String departmentCode, ActionStatus status, UserSummary requestedBy,
                               UserSummary reviewer, String reviewNote, String failureReason, OffsetDateTime createdAt,
                               OffsetDateTime expiresAt, OffsetDateTime decidedAt, boolean canReview) {

    public static ActionRequestDto of(ActionRequest r, boolean canReview) {
        return new ActionRequestDto(r.getId().toString(), r.getToolName(), r.getSummary(), Map.copyOf(r.getArguments()),
                r.getRequiredRole(), r.getDepartmentCode(), r.getStatus(), UserSummary.of(r.getRequestedBy()),
                UserSummary.of(r.getReviewer()), r.getReviewNote(), r.getFailureReason(),
                ClockConfig.ist(r.getCreatedAt()), ClockConfig.ist(r.getExpiresAt()),
                ClockConfig.ist(r.getDecidedAt()), canReview);
    }
}
