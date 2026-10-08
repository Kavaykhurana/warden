package edu.ashbrook.warden.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.ai.ToolEvent;
import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.NotFoundException;
import edu.ashbrook.warden.common.Text;
import edu.ashbrook.warden.conversation.ConversationDtos.ChatTurn;
import edu.ashbrook.warden.conversation.ConversationDtos.ConversationDto;
import edu.ashbrook.warden.conversation.ConversationDtos.MessageDto;
import edu.ashbrook.warden.user.CurrentUser;

/** Conversations are private to their owner; another user's id is answered with 404 so existence never leaks. */
@Service
public class ConversationService {

    private static final int TITLE_FROM_MESSAGE = 48;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               CurrentUser currentUser, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> list() {
        return conversations.findByUserIdOrderByUpdatedAtDesc(currentUser.id()).stream()
                .map(ConversationDto::of).toList();
    }

    @Transactional
    public ConversationDto create(String title) {
        String clean = Text.trim(title);
        if (clean.length() > 120) {
            throw new FieldValidationException("title", "Keep the title to 120 characters or fewer.");
        }
        Conversation c = conversations.save(new Conversation(currentUser.id(),
                clean.isEmpty() ? Conversation.DEFAULT_TITLE : clean, clock.instant()));
        return ConversationDto.of(c);
    }

    @Transactional
    public ConversationDto rename(UUID id, String title) {
        String clean = Text.trim(title);
        if (!Text.lengthBetween(clean, 1, 120)) {
            throw new FieldValidationException("title", "Use a title of 1 to 120 characters.");
        }
        Conversation c = owned(id);
        c.rename(clean);
        return ConversationDto.of(c);
    }

    @Transactional
    public void delete(UUID id) {
        conversations.delete(owned(id));
    }

    @Transactional(readOnly = true)
    public List<MessageDto> messages(UUID id) {
        owned(id);
        return messages.findByConversationIdOrderByIdAsc(id).stream().map(MessageDto::of).toList();
    }

    /** Throws 404 unless the conversation belongs to the signed-in user. */
    @Transactional(readOnly = true)
    public void requireOwned(UUID id) {
        owned(id);
    }

    /** The most recent messages, oldest first — the model's only memory. */
    @Transactional(readOnly = true)
    public List<Message> recent(UUID id, int limit) {
        List<Message> newestFirst = new ArrayList<>(messages.findByConversationIdOrderByIdDesc(id, Limit.of(limit)));
        return newestFirst.reversed();
    }

    /** Persists both halves of a turn in one short transaction. */
    @Transactional
    public ChatTurn saveTurn(UUID id, String userText, boolean flagged, String reply, List<ToolEvent> events) {
        Conversation c = owned(id);
        Instant now = clock.instant();
        Message user = messages.save(new Message(id, MessageRole.USER, userText, List.of(), flagged, now));
        Message assistant = messages.save(new Message(id, MessageRole.ASSISTANT, reply, List.copyOf(events), false,
                now.plusMillis(1)));
        c.touch(now);
        if (Conversation.DEFAULT_TITLE.equals(c.getTitle())) {
            c.rename(titleFrom(userText));
        }
        return new ChatTurn(MessageDto.of(user), MessageDto.of(assistant));
    }

    /** First 48 characters of the first message, trimmed, without trailing punctuation. */
    static String titleFrom(String text) {
        String flat = text.strip().replaceAll("\\s+", " ");
        String cut = flat.length() > TITLE_FROM_MESSAGE ? flat.substring(0, TITLE_FROM_MESSAGE) : flat;
        String title = cut.strip().replaceAll("[\\p{Punct}\\s]+$", "");
        return title.isEmpty() ? Conversation.DEFAULT_TITLE : title;
    }

    private Conversation owned(UUID id) {
        return conversations.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("That conversation doesn't exist."));
    }
}
