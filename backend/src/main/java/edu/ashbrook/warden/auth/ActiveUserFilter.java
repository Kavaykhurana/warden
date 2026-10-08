package edu.ashbrook.warden.auth;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import edu.ashbrook.warden.common.JsonAuthEntryPoint;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserRepository;

/**
 * Rejects a still-valid access token whose user has been deactivated, so deactivation bites within seconds rather
 * than at token expiry. Activity is cached for 30 seconds; deactivation evicts the entry immediately.
 */
@Component
public class ActiveUserFilter extends OncePerRequestFilter {

    private static final long TTL_NANOS = Duration.ofSeconds(30).toNanos();

    private record Entry(boolean active, long checkedNanos) {
    }

    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final UserRepository users;
    private final JsonAuthEntryPoint entryPoint;

    public ActiveUserFilter(UserRepository users, JsonAuthEntryPoint entryPoint) {
        this.users = users;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && !isActive(UUID.fromString(jwt.getName()))) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new DisabledException("Account is inactive"));
            return;
        }
        chain.doFilter(request, response);
    }

    public void evict(UUID userId) {
        cache.remove(userId);
    }

    private boolean isActive(UUID userId) {
        long now = System.nanoTime();
        Entry entry = cache.get(userId);
        if (entry == null || now - entry.checkedNanos() > TTL_NANOS) {
            entry = new Entry(users.findById(userId).map(User::isActive).orElse(false), now);
            cache.put(userId, entry);
        }
        return entry.active();
    }
}
