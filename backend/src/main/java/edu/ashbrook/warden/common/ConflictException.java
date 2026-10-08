package edu.ashbrook.warden.common;

/** Stale state, a double decision or an overlap (409). The message is a user-safe sentence. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
