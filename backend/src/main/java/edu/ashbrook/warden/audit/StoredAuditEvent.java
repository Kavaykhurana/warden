package edu.ashbrook.warden.audit;

import java.time.Instant;
import java.util.SortedMap;
import java.util.UUID;

/** An audit row exactly as stored — the input to chain verification. */
record StoredAuditEvent(long id, UUID actorId, String eventType, String toolName, SortedMap<String, String> details,
                        String prevHash, String hash, Instant createdAt) {

    String recomputeHash() {
        return HashChain.hash(prevHash, actorId, eventType, toolName, details, createdAt);
    }
}
