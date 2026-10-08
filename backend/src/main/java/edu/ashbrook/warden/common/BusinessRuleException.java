package edu.ashbrook.warden.common;

/** A domain rule was broken (422). The message is a user-safe sentence. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
