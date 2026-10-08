package edu.ashbrook.warden.common;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Field-level validation that annotations can't express (trimmed lengths, cross-field rules). Maps to 400. */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public FieldValidationException(Map<String, String> fieldErrors) {
        super("Some fields need attention.");
        this.fieldErrors = new TreeMap<>(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    /** One sentence for places that can't show per-field errors, such as tool results. */
    public String summary() {
        return fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(" "));
    }
}
