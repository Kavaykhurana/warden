package edu.ashbrook.warden.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditType;
import edu.ashbrook.warden.common.BusinessRuleException;
import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.common.FieldValidationException;

class ToolGuardTest {

    private final AuditService audit = mock(AuditService.class);
    private final ToolGuard guard = new ToolGuard(audit);
    private final UUID userId = UUID.randomUUID();
    private final Authentication auth = new UsernamePasswordAuthenticationToken(userId.toString(), null,
            AuthorityUtils.createAuthorityList("ROLE_STUDENT"));
    private final ToolEventCollector events = new ToolEventCollector();
    private final ToolContext context = new ToolContext(Map.of(ToolKeys.AUTH, auth, ToolKeys.EVENTS, events));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessDeniedBecomesTheToolsOwnDenialSentence() {
        String text = guard.run(context, "requestGradeChange", Map.of("courseCode", "UCS301"),
                "Only the faculty who teaches a course can request a grade change.", () -> {
                    throw new AuthorizationDeniedException("Access Denied");
                });
        assertThat(text).isEqualTo("DENIED: Only the faculty who teaches a course can request a grade change.");
        assertThat(events.snapshot()).singleElement().satisfies(e -> {
            assertThat(e.decision()).isEqualTo(Decision.DENIED);
            assertThat(e.arguments()).containsEntry("courseCode", "UCS301");
        });
        verify(audit).append(eq(userId), eq(AuditType.TOOL_DENIED), eq("requestGradeChange"), anyMap());
    }

    @Test
    void businessFailuresBecomeFailedWithTheirSafeMessage() {
        assertThat(guard.run(context, "a", Map.of(), "no", () -> {
            throw new BusinessRuleException("Grades can only be changed after they're published.");
        })).isEqualTo("FAILED: Grades can only be changed after they're published.");
        assertThat(guard.run(context, "b", Map.of(), "no", () -> {
            throw new ConflictException("A change for this student is already pending.");
        })).isEqualTo("FAILED: A change for this student is already pending.");
        assertThat(guard.run(context, "c", Map.of(), "no", () -> {
            throw new FieldValidationException("leaveId", "Use a whole number.");
        })).isEqualTo("FAILED: leaveId: Use a whole number.");
        assertThat(guard.run(context, "d", Map.of(), "no", () -> {
            throw new IllegalStateException("org.postgresql.SomeDetail at line 3");
        })).isEqualTo("FAILED: Internal error while running d.");
        verify(audit, org.mockito.Mockito.times(4)).append(eq(userId), eq(AuditType.TOOL_FAILED), any(), anyMap());
    }

    @Test
    void allowedAndQueuedOutcomesCarryPayloadAndRequestId() {
        assertThat(guard.run(context, "getMyGrades", Map.of(), "no", () -> ToolOutcome.allowed("Grades.", "UCS301: B+")))
                .isEqualTo("ALLOWED: Grades.\nUCS301: B+");
        assertThat(guard.run(context, "deactivateUser", Map.of("email", "x@y"), "no",
                () -> ToolOutcome.queued("Waiting.", "req-1"))).isEqualTo("QUEUED: Waiting.");
        assertThat(events.snapshot().get(1).actionRequestId()).isEqualTo("req-1");
        verify(audit).append(eq(userId), eq(AuditType.TOOL_QUEUED), eq("deactivateUser"),
                eq(Map.of("email", "x@y", "decision", "QUEUED", "actionRequestId", "req-1")));
    }

    @Test
    void moreThanSixCallsInOneTurnFailWithoutRunning() {
        AtomicInteger ran = new AtomicInteger();
        for (int i = 0; i < 6; i++) {
            guard.run(context, "getMyProfile", Map.of("n", String.valueOf(i)), "no", () -> {
                ran.incrementAndGet();
                return ToolOutcome.allowed("ok");
            });
        }
        String seventh = guard.run(context, "getMyProfile", Map.of("n", "7"), "no", () -> {
            ran.incrementAndGet();
            return ToolOutcome.allowed("ok");
        });
        guard.run(context, "getMyProfile", Map.of("n", "8"), "no", () -> ToolOutcome.allowed("ok"));
        assertThat(seventh).isEqualTo("FAILED: " + ToolGuard.TOO_MANY);
        assertThat(ran).hasValue(6);
        assertThat(events.snapshot()).hasSize(7);
    }

    @Test
    void anIdenticalCallInTheSameTurnRunsOnce() {
        AtomicInteger ran = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            assertThat(guard.run(context, "cancelDutyLeave", Map.of("leaveId", "4"), "no", () -> {
                ran.incrementAndGet();
                return ToolOutcome.allowed("Cancelled.");
            })).isEqualTo("ALLOWED: Cancelled.");
        }
        assertThat(ran).hasValue(1);
        assertThat(events.snapshot()).hasSize(1);
    }

    @Test
    void theCallersIdentityIsInstalledAndThePreviousContextRestored() {
        SecurityContext previous = SecurityContextHolder.createEmptyContext();
        previous.setAuthentication(new UsernamePasswordAuthenticationToken("someone-else", null, List.of()));
        SecurityContextHolder.setContext(previous);

        guard.run(context, "ok", Map.of(), "no", () -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(auth);
            return ToolOutcome.allowed("ok");
        });
        assertThat(SecurityContextHolder.getContext()).isSameAs(previous);

        guard.run(context, "boom", Map.of(), "no", () -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(auth);
            throw new IllegalStateException("boom");
        });
        assertThat(SecurityContextHolder.getContext()).isSameAs(previous);
    }

    @Test
    void noAuthenticationInTheContextIsDenied() {
        assertThat(guard.run(new ToolContext(Map.of()), "getMyProfile", Map.of(), "no", () -> ToolOutcome.allowed("x")))
                .isEqualTo("DENIED: No authenticated user.");
        verify(audit, never()).append(any(), any(), any(), anyMap());
    }

    @Test
    void noToolRunsAfterTheTurnIsClosed() {
        events.closeAndSnapshot();
        AtomicInteger ran = new AtomicInteger();
        assertThat(guard.run(context, "decideDutyLeave", Map.of(), "no", () -> {
            ran.incrementAndGet();
            return ToolOutcome.allowed("x");
        })).startsWith("FAILED");
        assertThat(ran).hasValue(0);
    }
}
