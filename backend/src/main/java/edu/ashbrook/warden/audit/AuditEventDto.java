package edu.ashbrook.warden.audit;

import java.time.OffsetDateTime;
import java.util.Map;

import edu.ashbrook.warden.user.UserSummary;

public record AuditEventDto(long id, UserSummary actor, AuditType eventType, String toolName,
                            Map<String, String> details, String prevHash, String hash, OffsetDateTime createdAt) {
}
