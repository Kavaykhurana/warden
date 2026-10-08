package edu.ashbrook.warden.auth;

import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditType;
import edu.ashbrook.warden.common.RateLimitedException;
import edu.ashbrook.warden.common.RateLimiter;

/** Five sign-in attempts per minute per client IP. */
@Component
public class LoginRateLimit {

    private final RateLimiter limiter;
    private final AuditService audit;

    public LoginRateLimit(RateLimiter limiter, AuditService audit) {
        this.limiter = limiter;
        this.audit = audit;
    }

    public void check(String clientIp) {
        if (!limiter.tryAcquire("login:" + clientIp, 5, Duration.ofMinutes(1))) {
            audit.append(null, AuditType.RATE_LIMITED, null, Map.of("scope", "login", "ip", clientIp));
            throw new RateLimitedException("Too many sign-in attempts. Wait a minute and try again.");
        }
    }
}
