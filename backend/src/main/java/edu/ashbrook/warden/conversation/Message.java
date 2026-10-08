package edu.ashbrook.warden.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import edu.ashbrook.warden.ai.ToolEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<ToolEvent> toolEvents;

    private boolean flagged;

    private Instant createdAt;

    protected Message() {
    }

    public Message(UUID conversationId, MessageRole role, String content, List<ToolEvent> toolEvents, boolean flagged,
                   Instant createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.toolEvents = toolEvents;
        this.flagged = flagged;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<ToolEvent> getToolEvents() {
        return toolEvents == null ? List.of() : toolEvents;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
