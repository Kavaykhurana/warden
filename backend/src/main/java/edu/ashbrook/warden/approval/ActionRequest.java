package edu.ashbrook.warden.approval;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "action_requests")
public class ActionRequest {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    private ActionTool toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> arguments;

    private String summary;

    @Enumerated(EnumType.STRING)
    private Role requiredRole;

    private String departmentCode;

    @Enumerated(EnumType.STRING)
    private ActionStatus status = ActionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    private String reviewNote;

    private String failureReason;

    private Instant expiresAt;

    private Instant decidedAt;

    @Version
    private long version;

    private Instant createdAt;

    protected ActionRequest() {
    }

    public ActionRequest(UUID conversationId, User requestedBy, ActionTool toolName, Map<String, String> arguments,
                         String summary, Role requiredRole, String departmentCode, Instant createdAt,
                         Instant expiresAt) {
        this.conversationId = conversationId;
        this.requestedBy = requestedBy;
        this.toolName = toolName;
        this.arguments = arguments;
        this.summary = summary;
        this.requiredRole = requiredRole;
        this.departmentCode = departmentCode;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void executed(User reviewer, String note, Instant at) {
        decide(ActionStatus.EXECUTED, reviewer, note, at);
    }

    public void failed(User reviewer, String note, String reason, Instant at) {
        decide(ActionStatus.FAILED, reviewer, note, at);
        this.failureReason = reason;
    }

    public void rejected(User reviewer, String note, Instant at) {
        decide(ActionStatus.REJECTED, reviewer, note, at);
    }

    private void decide(ActionStatus newStatus, User by, String note, Instant at) {
        this.status = newStatus;
        this.reviewer = by;
        this.reviewNote = note;
        this.decidedAt = at;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public ActionTool getToolName() {
        return toolName;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public String getSummary() {
        return summary;
    }

    public Role getRequiredRole() {
        return requiredRole;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public ActionStatus getStatus() {
        return status;
    }

    public User getReviewer() {
        return reviewer;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
