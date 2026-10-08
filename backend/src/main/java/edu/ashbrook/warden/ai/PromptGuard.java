package edu.ashbrook.warden.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Defence in depth only: a weighted regex scorer for obvious instruction-override attempts. It is not a classifier
 * and never the reason an action is denied — authorization lives in the services.
 */
@Component
public class PromptGuard {

    static final double FLAG_AT = 0.4;
    static final double BLOCK_AT = 0.8;

    private record Rule(String name, Pattern pattern, double weight) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Rule> RULES = List.of(
            rule("ignore-instructions", "ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|rules)", 0.6),
            rule("role-override", "\\b(you are now|act as|pretend to be)\\b.{0,30}\\b(admin|administrator|hod|system|developer|root)\\b", 0.4),
            rule("system-label", "(^|\\n)\\s*(system|developer)\\s*:|\\bsystem\\s*:", 0.4),
            rule("bypass-approval", "approve (it|this|them) yourself|bypass (the )?(approval|security|authori[sz]ation)", 0.5),
            rule("prompt-exfiltration", "(reveal|print|show|repeat).{0,20}(system prompt|hidden instructions)", 0.4),
            rule("fake-tags", "</?\\s*(system|assistant|tool|untrusted[^>]*)\\s*>", 0.3),
            rule("claimed-authority", "you are (authori[sz]ed|allowed|permitted) to", 0.3));

    private static Rule rule(String name, String regex, double weight) {
        return new Rule(name, Pattern.compile(regex, FLAGS), weight);
    }

    public GuardResult assess(String text) {
        if (text == null || text.isBlank()) {
            return new GuardResult(0, GuardResult.Verdict.PASS, List.of());
        }
        double score = 0;
        List<String> matched = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                score += rule.weight();
                matched.add(rule.name());
            }
        }
        score = Math.min(1.0, Math.round(score * 100) / 100.0);
        GuardResult.Verdict verdict = score >= BLOCK_AT ? GuardResult.Verdict.BLOCK
                : score >= FLAG_AT ? GuardResult.Verdict.FLAG : GuardResult.Verdict.PASS;
        return new GuardResult(score, verdict, List.copyOf(matched));
    }
}
