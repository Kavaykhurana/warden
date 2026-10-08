package edu.ashbrook.warden.audit;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Canonical form of one audit event. Each detail key and value is length-prefixed, so no choice of strings can make
 * two different events serialize identically (for example {@code a|b} versus {@code a} followed by {@code |b}).
 */
public final class HashChain {

    public static final String GENESIS = "0".repeat(64);

    private HashChain() {
    }

    public static String hash(String prev, UUID actor, String type, String tool, SortedMap<String, String> details,
                              Instant at) {
        StringBuilder sb = new StringBuilder(prev)
                .append('|').append(actor == null ? "system" : actor)
                .append('|').append(type)
                .append('|').append(tool == null ? "" : tool)
                .append('|').append(ChronoUnit.MICROS.between(Instant.EPOCH, at));
        details.forEach((k, v) -> sb.append('|').append(k.length()).append(':').append(k)
                .append(v.length()).append(':').append(v));
        return HexFormat.of().formatHex(sha256(sb.toString().getBytes(UTF_8)));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
