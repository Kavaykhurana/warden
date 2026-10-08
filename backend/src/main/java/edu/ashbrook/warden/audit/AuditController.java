package edu.ashbrook.warden.audit;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.PageDto;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    PageDto<AuditEventDto> page(@RequestParam(required = false) String eventType,
                                @RequestParam(required = false) UUID actorId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "25") int size) {
        AuditType type = eventType == null || eventType.isBlank() ? null : parse(eventType);
        return audit.page(type, actorId, from == null ? null : from.toInstant(), page, size);
    }

    @GetMapping("/verify")
    ChainVerification verify() {
        return audit.verify();
    }

    private static AuditType parse(String eventType) {
        return Arrays.stream(AuditType.values()).filter(t -> t.name().equals(eventType)).findFirst()
                .orElseThrow(() -> new FieldValidationException("eventType", "Unknown event type."));
    }
}
