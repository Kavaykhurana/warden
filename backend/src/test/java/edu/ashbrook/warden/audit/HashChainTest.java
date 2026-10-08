package edu.ashbrook.warden.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HashChainTest {

    private static final UUID ACTOR = UUID.fromString("6f1c2a52-7d0e-4c55-9a38-1f0f6a9f2b11");
    private static final Instant AT = Instant.parse("2026-10-08T09:51:04.123456Z");

    private static SortedMap<String, String> details(String... kv) {
        SortedMap<String, String> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String base() {
        return HashChain.hash(HashChain.GENESIS, ACTOR, "TOOL_DENIED", "requestGradeChange",
                details("courseCode", "UCS301", "decision", "DENIED"), AT);
    }

    @Test
    void isDeterministicHexSha256() {
        assertThat(base()).isEqualTo(base()).matches("[0-9a-f]{64}");
    }

    @Test
    void everyFieldChangesTheHash() {
        String h = base();
        SortedMap<String, String> d = details("courseCode", "UCS301", "decision", "DENIED");
        assertThat(HashChain.hash("1".repeat(64), ACTOR, "TOOL_DENIED", "requestGradeChange", d, AT)).isNotEqualTo(h);
        assertThat(HashChain.hash(HashChain.GENESIS, null, "TOOL_DENIED", "requestGradeChange", d, AT)).isNotEqualTo(h);
        assertThat(HashChain.hash(HashChain.GENESIS, UUID.randomUUID(), "TOOL_DENIED", "requestGradeChange", d, AT)).isNotEqualTo(h);
        assertThat(HashChain.hash(HashChain.GENESIS, ACTOR, "TOOL_ALLOWED", "requestGradeChange", d, AT)).isNotEqualTo(h);
        assertThat(HashChain.hash(HashChain.GENESIS, ACTOR, "TOOL_DENIED", null, d, AT)).isNotEqualTo(h);
        assertThat(HashChain.hash(HashChain.GENESIS, ACTOR, "TOOL_DENIED", "requestGradeChange", d, AT.plusNanos(1000)))
                .isNotEqualTo(h);
    }

    @Test
    void detailKeysAndValuesChangeTheHash() {
        String h = base();
        assertThat(HashChain.hash(HashChain.GENESIS, ACTOR, "TOOL_DENIED", "requestGradeChange",
                details("courseCode", "UCS303", "decision", "DENIED"), AT)).isNotEqualTo(h);
        assertThat(HashChain.hash(HashChain.GENESIS, ACTOR, "TOOL_DENIED", "requestGradeChange",
                details("course", "UCS301", "decision", "DENIED"), AT)).isNotEqualTo(h);
        assertThat(HashChain.hash(HashChain.GENESIS, ACTOR, "TOOL_DENIED", "requestGradeChange",
                details("courseCode", "UCS301"), AT)).isNotEqualTo(h);
    }

    @Test
    void lengthPrefixingPreventsDelimiterCollisions() {
        String joined = HashChain.hash(HashChain.GENESIS, null, "X", null, details("a", "b|c"), AT);
        String split = HashChain.hash(HashChain.GENESIS, null, "X", null, details("a", "b", "c", ""), AT);
        assertThat(joined).isNotEqualTo(split);
        assertThat(HashChain.hash(HashChain.GENESIS, null, "X", null, details("a|b", ""), AT))
                .isNotEqualTo(HashChain.hash(HashChain.GENESIS, null, "X", null, details("a", "|b"), AT));
        assertThat(HashChain.hash(HashChain.GENESIS, null, "X", null, details("ab", "c"), AT))
                .isNotEqualTo(HashChain.hash(HashChain.GENESIS, null, "X", null, details("a", "bc"), AT));
    }
}
