package edu.ashbrook.warden.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    private UUID familyId;

    private String tokenHash;

    private Instant expiresAt;

    private Instant revokedAt;

    private UUID replacedBy;

    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void rotatedTo(RefreshToken next, Instant at) {
        this.revokedAt = at;
        this.replacedBy = next.getId();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }
}
