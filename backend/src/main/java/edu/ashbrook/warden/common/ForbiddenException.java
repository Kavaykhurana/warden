package edu.ashbrook.warden.common;

import org.springframework.security.access.AccessDeniedException;

/** An {@link AccessDeniedException} whose message is safe and specific enough to show the user. */
public class ForbiddenException extends AccessDeniedException {

    public ForbiddenException(String message) {
        super(message);
    }
}
