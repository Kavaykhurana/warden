package edu.ashbrook.warden.audit;

import java.time.OffsetDateTime;

public record ChainVerification(boolean intact, long verifiedCount, Long brokenAtId, OffsetDateTime verifiedAt) {
}
