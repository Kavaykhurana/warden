package edu.ashbrook.warden.academics;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "grade_changes")
public class GradeChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long enrollmentId;

    private Grade oldGrade;

    private Grade newGrade;

    private String reason;

    private UUID requestedBy;

    private UUID approvedBy;

    private UUID actionRequestId;

    private Instant createdAt;

    protected GradeChange() {
    }

    public GradeChange(Long enrollmentId, Grade oldGrade, Grade newGrade, String reason, UUID requestedBy,
                       UUID approvedBy, UUID actionRequestId, Instant createdAt) {
        this.enrollmentId = enrollmentId;
        this.oldGrade = oldGrade;
        this.newGrade = newGrade;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.approvedBy = approvedBy;
        this.actionRequestId = actionRequestId;
        this.createdAt = createdAt;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public Grade getNewGrade() {
        return newGrade;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }
}
