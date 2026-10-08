package edu.ashbrook.warden.dutyleave;

import java.time.Instant;
import java.time.LocalDate;

import edu.ashbrook.warden.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "duty_leave_requests")
public class DutyLeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    private LocalDate startDate;

    private LocalDate endDate;

    private int workingDays;

    private String eventName;

    private String reason;

    private boolean reasonFlagged;

    private boolean requiresHod;

    @Enumerated(EnumType.STRING)
    private LeaveStatus status = LeaveStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    private String decisionNote;

    private Instant decidedAt;

    @Version
    private long version;

    private Instant createdAt;

    protected DutyLeaveRequest() {
    }

    public DutyLeaveRequest(User student, LocalDate startDate, LocalDate endDate, int workingDays, String eventName,
                            String reason, boolean reasonFlagged, Instant createdAt) {
        this.student = student;
        this.startDate = startDate;
        this.endDate = endDate;
        this.workingDays = workingDays;
        this.eventName = eventName;
        this.reason = reason;
        this.reasonFlagged = reasonFlagged;
        this.requiresHod = workingDays > 3;
        this.createdAt = createdAt;
    }

    public void decide(boolean approve, User decider, String note, Instant at) {
        this.status = approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED;
        this.decidedBy = decider;
        this.decisionNote = note;
        this.decidedAt = at;
    }

    public void cancel() {
        this.status = LeaveStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getWorkingDays() {
        return workingDays;
    }

    public String getEventName() {
        return eventName;
    }

    public String getReason() {
        return reason;
    }

    public boolean isReasonFlagged() {
        return reasonFlagged;
    }

    public boolean isRequiresHod() {
        return requiresHod;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
