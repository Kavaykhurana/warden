package edu.ashbrook.warden.conversation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversations")
public class Conversation {

    public static final String DEFAULT_TITLE = "New conversation";

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    private String title;

    private Instant createdAt;

    private Instant updatedAt;

    protected Conversation() {
    }

    public Conversation(UUID userId, String title, Instant now) {
        this.userId = userId;
        this.title = title;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void rename(String title) {
        this.title = title;
    }

    public void touch(Instant at) {
        this.updatedAt = at;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
