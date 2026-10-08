package edu.ashbrook.warden.ai;

import java.util.List;

public record GuardResult(double score, Verdict verdict, List<String> matchedRules) {

    public enum Verdict { PASS, FLAG, BLOCK }

    public boolean flagged() {
        return verdict != Verdict.PASS;
    }

    public boolean blocked() {
        return verdict == Verdict.BLOCK;
    }

    /** Score as a stable two-decimal string for audit details. */
    public String scoreText() {
        return String.format(java.util.Locale.ROOT, "%.2f", score);
    }
}
