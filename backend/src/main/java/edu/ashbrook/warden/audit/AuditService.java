package edu.ashbrook.warden.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.PageDto;
import edu.ashbrook.warden.common.Text;
import edu.ashbrook.warden.config.ClockConfig;
import tools.jackson.databind.json.JsonMapper;

/** Append-only, hash-chained audit log. Rows are only ever inserted; a database trigger rejects UPDATE and DELETE. */
@Service
public class AuditService {

    static final int MAX_DETAIL_LENGTH = 300;
    private static final int VERIFY_CHUNK = 500;

    private final AuditQueryRepository repository;
    private final JsonMapper json;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    public AuditService(AuditQueryRepository repository, JsonMapper json, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Records an event in its own transaction, so a DENIED or FAILED event survives even when the business
     * transaction around it rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(UUID actorId, AuditType type, String toolName, Map<String, String> details) {
        insert(actorId, type, toolName, details, clock.instant());
    }

    /**
     * Records an outcome only once the surrounding transaction has committed — used for decisions (approved,
     * executed, rejected) so the log never claims something that was rolled back. Appends immediately when no
     * transaction is active.
     */
    public void appendAfterCommit(UUID actorId, AuditType type, String toolName, Map<String, String> details) {
        Instant at = clock.instant();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            requiresNew.executeWithoutResult(s -> insert(actorId, type, toolName, details, at));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                requiresNew.executeWithoutResult(s -> insert(actorId, type, toolName, details, clock.instant()));
            }
        });
    }

    /** Backdated append for the demo seeder. Joins the caller's transaction so it can reference freshly seeded users. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendAt(UUID actorId, AuditType type, String toolName, Map<String, String> details, Instant at) {
        insert(actorId, type, toolName, details, at);
    }

    private void insert(UUID actorId, AuditType type, String toolName, Map<String, String> details, Instant at) {
        repository.lockChain();
        String prev = repository.latestHash();
        Instant timestamp = at.truncatedTo(ChronoUnit.MICROS);
        SortedMap<String, String> clean = new TreeMap<>();
        details.forEach((k, v) -> clean.put(Text.truncate(k, 60), Text.truncate(v, MAX_DETAIL_LENGTH)));
        String hash = HashChain.hash(prev, actorId, type.name(), toolName, clean, timestamp);
        repository.insert(actorId, type, toolName, json.writeValueAsString(clean), prev, hash, timestamp);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ChainVerification verify() {
        String expectedPrev = HashChain.GENESIS;
        long verified = 0;
        long lastId = 0;
        List<StoredAuditEvent> chunk;
        do {
            chunk = repository.chunkAfter(lastId, VERIFY_CHUNK);
            for (StoredAuditEvent event : chunk) {
                if (!event.prevHash().equals(expectedPrev) || !event.recomputeHash().equals(event.hash())) {
                    return new ChainVerification(false, verified, event.id(), ClockConfig.ist(clock.instant()));
                }
                expectedPrev = event.hash();
                verified++;
                lastId = event.id();
            }
        } while (chunk.size() == VERIFY_CHUNK);
        return new ChainVerification(true, verified, null, ClockConfig.ist(clock.instant()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AuditSummary summary(int hours) {
        if (hours < 1 || hours > 168) {
            throw new FieldValidationException("hours", "Use a number of hours from 1 to 168.");
        }
        Instant since = clock.instant().minus(hours, ChronoUnit.HOURS);
        return new AuditSummary(hours, repository.countsByType(since), repository.topActors(since));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageDto<AuditEventDto> page(AuditType type, UUID actorId, Instant from, int page, int size) {
        if (page < 0) {
            throw new FieldValidationException("page", "Page must be 0 or more.");
        }
        if (size < 1 || size > 100) {
            throw new FieldValidationException("size", "Page size must be between 1 and 100.");
        }
        return repository.page(type, actorId, from, page, size);
    }
}
