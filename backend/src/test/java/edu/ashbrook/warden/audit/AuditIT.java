package edu.ashbrook.warden.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import edu.ashbrook.warden.support.IntegrationTest;

class AuditIT extends IntegrationTest {

    @Autowired
    private AuditService audit;

    @Test
    void seededChainVerifiesAndAppendsKeepItIntact() throws Exception {
        ChainVerification seeded = runAs(PRIYA, audit::verify);
        assertThat(seeded.intact()).isTrue();
        assertThat(seeded.verifiedCount()).isGreaterThanOrEqualTo(24);
        long before = seeded.verifiedCount();

        audit.append(id(MEERA), AuditType.TOOL_ALLOWED, "getMyProfile", Map.of("decision", "ALLOWED"));
        audit.append(null, AuditType.ACTION_EXPIRED, null, Map.of("actionRequestId", "x".repeat(500)));
        mvc.perform(get("/api/admin/audit/verify").with(as(PRIYA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.verifiedCount").value((int) before + 2))
                .andExpect(jsonPath("$.brokenAtId").isEmpty());
        assertThat(jdbc.queryForObject("select length(details->>'actionRequestId') from audit_events "
                + "order by id desc limit 1", Integer.class)).isEqualTo(300);
    }

    @Test
    void truncationNeverSplitsAnEmojiSoTheChainStillVerifies() {
        // 299 ASCII characters then an emoji: a naive cut at 300 UTF-16 units leaves half a surrogate pair,
        // which is stored as '?'. The cut must drop the whole emoji instead.
        audit.append(id(KABIR), AuditType.TOOL_ALLOWED, "applyDutyLeave", Map.of("reason", "x".repeat(299) + "\uD83C\uDFC0 ball"));
        String stored = jdbc.queryForObject("select details->>'reason' from audit_events order by id desc limit 1", String.class);
        assertThat(stored).isEqualTo("x".repeat(299));
        assertThat(runAs(PRIYA, audit::verify).intact()).isTrue();
    }

    @Test
    void theTableIsAppendOnly() {
        assertThatThrownBy(() -> jdbc.update("update audit_events set tool_name = 'x' where id = 1"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_events where id = 1"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void tamperingIsDetectedAtTheChangedRow() throws Exception {
        long target = jdbc.queryForObject("select id from audit_events where event_type = 'TOOL_DENIED' limit 1", Long.class);
        String original = jdbc.queryForObject("select details::text from audit_events where id = ?", String.class, target);
        tamper("update audit_events set details = jsonb_set(details, '{newGrade}', '\"B\"') where id = ?", target);
        try {
            mvc.perform(get("/api/admin/audit/verify").with(as(VIKRAM)))
                    .andExpect(jsonPath("$.intact").value(false))
                    .andExpect(jsonPath("$.brokenAtId").value(target));
        } finally {
            tamper("update audit_events set details = cast(? as jsonb) where id = ?", original, target);
        }
        mvc.perform(get("/api/admin/audit/verify").with(as(VIKRAM))).andExpect(jsonPath("$.intact").value(true));
    }

    /** Test-only: bypass the append-only trigger to simulate someone editing the table directly. */
    private void tamper(String sql, Object... args) {
        jdbc.execute("alter table audit_events disable trigger trg_audit_immutable");
        try {
            jdbc.update(sql, args);
        } finally {
            jdbc.execute("alter table audit_events enable trigger trg_audit_immutable");
        }
    }

    @Test
    void pagesAreNewestFirstAndFilterable() throws Exception {
        mvc.perform(get("/api/admin/audit?size=5").with(as(PRIYA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
        mvc.perform(get("/api/admin/audit?eventType=LOGIN_FAILURE").with(as(PRIYA)))
                .andExpect(jsonPath("$.content[0].eventType").value("LOGIN_FAILURE"))
                .andExpect(jsonPath("$.content[0].actor.fullName").value("Aarav Sharma"));
        mvc.perform(get("/api/admin/audit?actorId=" + id(SUNITA)).with(as(PRIYA)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/admin/audit").param("from", "2026-10-08T05:00:00+05:30").with(as(PRIYA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)));
        mvc.perform(get("/api/admin/audit?size=101").with(as(PRIYA))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit?eventType=NOPE").with(as(PRIYA))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit").with(as(SUNITA))).andExpect(status().isForbidden());
    }

    @Test
    void summaryCountsByTypeAndTopActors() {
        AuditSummary summary = runAs(PRIYA, () -> audit.summary(168));
        assertThat(summary.countsByType()).containsEntry("LOGIN_SUCCESS", 8L);
        assertThat(summary.topActors()).hasSizeLessThanOrEqualTo(5).isNotEmpty();
    }
}
