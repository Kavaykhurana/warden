package edu.ashbrook.warden.common;

public class RateLimitedException extends RuntimeException {

    public RateLimitedException(String message) {
        super(message);
    }
}
