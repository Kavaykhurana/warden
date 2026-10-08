package edu.ashbrook.warden.ai;

import java.util.Map;

/** One tool call as shown to the user. Stored in {@code messages.tool_events}. */
public record ToolEvent(String tool, Map<String, String> arguments, Decision decision, String message,
                        String actionRequestId) {
}
