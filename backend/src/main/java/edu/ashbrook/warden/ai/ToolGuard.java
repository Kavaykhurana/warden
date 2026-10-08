package edu.ashbrook.warden.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditType;
import edu.ashbrook.warden.common.BusinessRuleException;
import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.NotFoundException;
import edu.ashbrook.warden.common.Text;
import edu.ashbrook.warden.common.UnauthorizedException;

/**
 * Every tool call goes through here. The model is an untrusted planner: this runs the call as the signed-in user
 * (so the services' {@code @PreAuthorize} decides), turns any failure into a one-line outcome, records it for the
 * user and appends it to the audit log.
 */
@Component
public class ToolGuard {

    private static final Logger log = LoggerFactory.getLogger(ToolGuard.class);
    static final String NO_USER = "No authenticated user.";
    static final String TOO_MANY = "Too many actions for one message. Ask for one thing at a time.";
    private static final int AUDIT_ARG_LENGTH = 120;

    private final AuditService audit;

    public ToolGuard(AuditService audit) {
        this.audit = audit;
    }

    /**
     * @param denial the sentence shown when authorization fails, naming the role that is needed
     */
    public String run(ToolContext context, String tool, Map<String, String> args, String denial,
                      Supplier<ToolOutcome> action) {
        Map<String, Object> values = context == null || context.getContext() == null ? Map.of() : context.getContext();
        if (!(values.get(ToolKeys.AUTH) instanceof Authentication auth)
                || !(values.get(ToolKeys.EVENTS) instanceof ToolEventCollector events)) {
            return ToolOutcome.denied(NO_USER).toModelText();
        }
        SortedMap<String, String> shown = new TreeMap<>();
        args.forEach((k, v) -> shown.put(k, Text.truncate(v, 300)));
        String key = tool + shown;
        events.lock();
        try {
            if (events.isClosed()) {
                return ToolOutcome.failed("This reply was cancelled before the action ran.").toModelText();
            }
            ToolOutcome previous = events.previous(key);
            if (previous != null) {
                return previous.toModelText();
            }
            ToolOutcome outcome;
            if (!events.tryCount()) {
                outcome = ToolOutcome.failed(TOO_MANY);
                if (!events.firstOverCap()) {
                    return outcome.toModelText();
                }
            } else {
                outcome = runAs(auth, tool, denial, action);
            }
            events.record(key, new ToolEvent(tool, Map.copyOf(shown), outcome.decision(), outcome.message(),
                    outcome.actionRequestId()), outcome);
            audit(auth, tool, shown, outcome);
            return outcome.toModelText();
        } finally {
            events.unlock();
        }
    }

    /** Tools may run on a model-client thread, so the caller's identity is installed explicitly and always restored. */
    private ToolOutcome runAs(Authentication auth, String tool, String denial, Supplier<ToolOutcome> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } catch (AccessDeniedException e) {
            return ToolOutcome.denied(denial);
        } catch (AuthenticationException | UnauthorizedException e) {
            return ToolOutcome.denied(NO_USER);
        } catch (FieldValidationException e) {
            return ToolOutcome.failed(e.summary());
        } catch (BusinessRuleException | ConflictException | NotFoundException e) {
            return ToolOutcome.failed(e.getMessage());
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException e) {
            return ToolOutcome.failed("Someone else changed this at the same time. Try again.");
        } catch (RuntimeException e) {
            log.error("Tool {} failed", tool, e);
            return ToolOutcome.failed("Internal error while running " + tool + ".");
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void audit(Authentication auth, String tool, SortedMap<String, String> args, ToolOutcome outcome) {
        Map<String, String> details = new LinkedHashMap<>();
        args.forEach((k, v) -> details.put(k, Text.truncate(v, AUDIT_ARG_LENGTH)));
        details.put("decision", outcome.decision().name());
        if (outcome.actionRequestId() != null) {
            details.put("actionRequestId", outcome.actionRequestId());
        }
        AuditType type = switch (outcome.decision()) {
            case ALLOWED -> AuditType.TOOL_ALLOWED;
            case DENIED -> AuditType.TOOL_DENIED;
            case QUEUED -> AuditType.TOOL_QUEUED;
            case FAILED -> AuditType.TOOL_FAILED;
        };
        audit.append(UUID.fromString(auth.getName()), type, tool, details);
    }
}
