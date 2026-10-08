package edu.ashbrook.warden.conversation;

import java.time.OffsetDateTime;
import java.util.List;

import edu.ashbrook.warden.ai.ToolEvent;
import edu.ashbrook.warden.config.ClockConfig;

/** Contract types {@code Conversation}, {@code Message} and {@code ChatTurn}. */
public final class ConversationDtos {

    private ConversationDtos() {
    }

    public record ConversationDto(String id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        static ConversationDto of(Conversation c) {
            return new ConversationDto(c.getId().toString(), c.getTitle(), ClockConfig.ist(c.getCreatedAt()),
                    ClockConfig.ist(c.getUpdatedAt()));
        }
    }

    public record MessageDto(long id, MessageRole role, String content, List<ToolEvent> toolEvents, boolean flagged,
                             OffsetDateTime createdAt) {
        static MessageDto of(Message m) {
            return new MessageDto(m.getId(), m.getRole(), m.getContent(), m.getToolEvents(), m.isFlagged(),
                    ClockConfig.ist(m.getCreatedAt()));
        }
    }

    public record ChatTurn(MessageDto userMessage, MessageDto assistantMessage) {
    }

    public record TitleRequest(String title) {
    }

    public record SendRequest(String content) {
    }
}
