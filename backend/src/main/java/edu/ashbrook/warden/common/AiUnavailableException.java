package edu.ashbrook.warden.common;

public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(Throwable cause) {
        super("The assistant is unavailable right now. Try again in a minute.", cause);
    }
}
