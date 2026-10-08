package edu.ashbrook.warden.ai;

/** What a tool did, as reported to the model ({@link #toModelText()}) and to the user (a {@link ToolEvent}). */
public record ToolOutcome(Decision decision, String message, String actionRequestId, String payload) {

    public static ToolOutcome allowed(String message) {
        return new ToolOutcome(Decision.ALLOWED, message, null, null);
    }

    public static ToolOutcome allowed(String message, String payload) {
        return new ToolOutcome(Decision.ALLOWED, message, null, payload);
    }

    public static ToolOutcome queued(String message, String actionRequestId) {
        return new ToolOutcome(Decision.QUEUED, message, actionRequestId, null);
    }

    public static ToolOutcome denied(String message) {
        return new ToolOutcome(Decision.DENIED, message, null, null);
    }

    public static ToolOutcome failed(String message) {
        return new ToolOutcome(Decision.FAILED, message, null, null);
    }

    /** {@code "<DECISION>: <message>"}, then any data on the following lines. */
    public String toModelText() {
        String head = decision + ": " + message;
        return payload == null || payload.isBlank() ? head : head + "\n" + payload;
    }
}
