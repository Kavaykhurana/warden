package edu.ashbrook.warden.approval;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditType;

/** Marks PENDING requests past their 48-hour window as EXPIRED. */
@Component
public class ApprovalExpiryJob {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public ApprovalExpiryJob(JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public int expireDue() {
        var now = clock.instant().atOffset(ZoneOffset.UTC);
        List<UUID> expired = jdbc.queryForList(
                "update action_requests set status = 'EXPIRED', decided_at = ?, version = version + 1 "
                        + "where status = 'PENDING' and expires_at <= ? returning id",
                UUID.class, now, now);
        expired.forEach(id -> audit.appendAfterCommit(null, AuditType.ACTION_EXPIRED, null,
                Map.of("actionRequestId", id.toString())));
        return expired.size();
    }
}
