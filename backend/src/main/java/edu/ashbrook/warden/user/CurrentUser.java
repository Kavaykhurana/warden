package edu.ashbrook.warden.user;

import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import edu.ashbrook.warden.common.UnauthorizedException;

/**
 * The signed-in user, always taken from the {@code SecurityContext} — never from a request body or a model argument.
 * The AI path installs the same context before every tool call, so both paths resolve identically.
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public UUID id() {
        return idOf(SecurityContextHolder.getContext().getAuthentication());
    }

    /** The active user entity. Call inside a transaction when lazy associations are needed. */
    public User user() {
        return users.findById(id()).filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Sign in to continue."));
    }

    public static UUID idOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return UUID.fromString(authentication.getName());
    }
}
