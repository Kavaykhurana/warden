package edu.ashbrook.warden.ai;

/** Keys of the per-request tool context handed to Spring AI. */
public final class ToolKeys {

    public static final String AUTH = "AUTH";
    public static final String EVENTS = "EVENTS";
    public static final String CONVERSATION_ID = "CONVERSATION_ID";

    private ToolKeys() {
    }
}
