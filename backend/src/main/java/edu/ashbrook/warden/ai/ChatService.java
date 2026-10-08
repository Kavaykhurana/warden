package edu.ashbrook.warden.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditType;
import edu.ashbrook.warden.common.AiUnavailableException;
import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.RateLimitedException;
import edu.ashbrook.warden.common.RateLimiter;
import edu.ashbrook.warden.common.Text;
import edu.ashbrook.warden.conversation.ConversationDtos.ChatTurn;
import edu.ashbrook.warden.conversation.ConversationService;
import edu.ashbrook.warden.conversation.Message;
import edu.ashbrook.warden.conversation.MessageRole;
import edu.ashbrook.warden.user.UserDto;
import edu.ashbrook.warden.user.UserService;

/**
 * One chat turn. Deliberately not {@code @Transactional}: no database transaction or row lock is held while the
 * model is thinking. The user's identity travels to the tools in the tool context.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final String BLOCKED_REPLY = "I can't act on that message — it reads like an attempt to override my "
            + "instructions. Ask me in plain words what you need and I'll check whether your role allows it.";
    static final String PARTIAL_REPLY = "I processed the actions below, but couldn't finish writing a reply.";
    private static final int HISTORY = 12;

    private final ChatClient chatClient;
    private final WardenTools tools;
    private final PromptGuard promptGuard;
    private final ConversationService conversations;
    private final UserService users;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock;
    private final Duration timeout;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService modelCalls = Executors.newVirtualThreadPerTaskExecutor();

    public ChatService(ChatClient chatClient, WardenTools tools, PromptGuard promptGuard,
                       ConversationService conversations, UserService users,
                       RateLimiter rateLimiter, AuditService audit, Clock clock,
                       @Value("${warden.ai.timeout}") Duration timeout) {
        this.chatClient = chatClient;
        this.tools = tools;
        this.promptGuard = promptGuard;
        this.conversations = conversations;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
        this.timeout = timeout;
    }

    public ChatTurn send(UUID conversationId, String content) {
        UserDto user = users.me();
        UUID userId = UUID.fromString(user.id());
        conversations.requireOwned(conversationId);
        String text = Text.trim(content);
        if (!Text.lengthBetween(text, 1, 2000)) {
            throw new FieldValidationException("content", "Write a message of 1 to 2000 characters.");
        }
        if (!rateLimiter.tryAcquire("chat:" + userId, 20, Duration.ofMinutes(1))) {
            audit.append(userId, AuditType.RATE_LIMITED, null, Map.of("scope", "chat"));
            throw new RateLimitedException("You're sending messages too quickly. Wait a minute and try again.");
        }
        if (!inFlight.add(conversationId)) {
            throw new ConflictException("A reply is still being written.");
        }
        try {
            return respond(user, userId, conversationId, text);
        } finally {
            inFlight.remove(conversationId);
        }
    }

    /** True while a reply for this conversation is being written (its actions are not saved yet). */
    public boolean isReplying(UUID conversationId) {
        return inFlight.contains(conversationId);
    }

    private ChatTurn respond(UserDto user, UUID userId, UUID conversationId, String text) {
        GuardResult guard = promptGuard.assess(text);
        Map<String, String> guardDetails = Map.of("source", "user", "score", guard.scoreText(),
                "length", String.valueOf(text.length()));
        if (guard.blocked()) {
            audit.append(userId, AuditType.INJECTION_BLOCKED, null, guardDetails);
            return conversations.saveTurn(conversationId, text, true, BLOCKED_REPLY, List.of());
        }
        if (guard.flagged()) {
            audit.append(userId, AuditType.INJECTION_FLAGGED, null, guardDetails);
        }
        List<org.springframework.ai.chat.messages.Message> history = history(conversations.recent(conversationId, HISTORY));
        String system = SystemPrompt.render(user, LocalDate.now(clock));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        ToolEventCollector events = new ToolEventCollector();
        Map<String, Object> toolContext = Map.of(ToolKeys.AUTH, auth, ToolKeys.EVENTS, events,
                ToolKeys.CONVERSATION_ID, conversationId);

        String reply;
        Future<String> call = modelCalls.submit(() -> chatClient.prompt()
                .system(system)
                .messages(history)
                .user(text)
                .tools(tools)
                .toolContext(toolContext)
                .call()
                .content());
        try {
            reply = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reply = null;
            log.warn("Chat turn interrupted");
        } catch (TimeoutException | ExecutionException e) {
            call.cancel(true);
            log.warn("Model call failed: {}", e instanceof TimeoutException ? "timeout" : rootMessage(e));
            reply = null;
        }
        // Closing waits for a tool that is still running and stops any later one, so what we persist is complete.
        List<ToolEvent> recorded = events.closeAndSnapshot();
        if (reply == null) {
            if (recorded.isEmpty()) {
                throw new AiUnavailableException(null);
            }
            reply = PARTIAL_REPLY;
        } else if (reply.isBlank()) {
            reply = recorded.isEmpty() ? "I couldn't produce a reply. Try rephrasing." : "Done.";
        }
        return conversations.saveTurn(conversationId, text, guard.flagged(), reply.strip(), recorded);
    }

    /** Stored messages as model history; blocked turns are left out so an injection is never replayed. */
    private static List<org.springframework.ai.chat.messages.Message> history(List<Message> stored) {
        List<org.springframework.ai.chat.messages.Message> out = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            Message m = stored.get(i);
            boolean blockedPair = m.getRole() == MessageRole.USER && i + 1 < stored.size()
                    && BLOCKED_REPLY.equals(stored.get(i + 1).getContent());
            if (blockedPair) {
                i++;
                continue;
            }
            if (m.getRole() == MessageRole.USER) {
                out.add(new UserMessage(m.getContent()));
            } else if (!BLOCKED_REPLY.equals(m.getContent())) {
                out.add(new AssistantMessage(withActions(m)));
            }
        }
        return out;
    }

    private static String withActions(Message m) {
        if (m.getToolEvents().isEmpty()) {
            return m.getContent();
        }
        return m.getContent() + "\n[actions: " + m.getToolEvents().stream()
                .map(e -> e.tool() + " " + e.decision())
                .collect(Collectors.joining(", ")) + "]";
    }

    /** Provider status for the log (e.g. a 429 quota message) — never the prompt or the user's text. */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + Text.truncate(root.getMessage(), 200);
    }

    @PreDestroy
    void shutdown() {
        modelCalls.shutdownNow();
    }
}
