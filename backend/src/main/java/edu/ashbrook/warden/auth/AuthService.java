package edu.ashbrook.warden.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.audit.AuditService;
import edu.ashbrook.warden.audit.AuditType;
import edu.ashbrook.warden.common.Text;
import edu.ashbrook.warden.common.UnauthorizedException;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserDto;
import edu.ashbrook.warden.user.UserRepository;

@Service
public class AuthService {

    public record AuthResult(AuthResponse response, String refreshToken) {
    }

    private static final String BAD_CREDENTIALS = "Email or password is incorrect.";

    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimit loginRateLimit;
    private final AuditService audit;
    private final Clock clock;
    private final Duration refreshTtl;
    /** Compared against when the email is unknown, so response time doesn't reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository tokens, TokenService tokenService,
                       PasswordEncoder passwordEncoder, LoginRateLimit loginRateLimit, AuditService audit, Clock clock,
                       @Value("${warden.refresh.ttl}") Duration refreshTtl) {
        this.users = users;
        this.tokens = tokens;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimit = loginRateLimit;
        this.audit = audit;
        this.clock = clock;
        this.refreshTtl = refreshTtl;
        this.dummyHash = passwordEncoder.encode(Hashing.randomToken());
    }

    @Transactional
    public AuthResult login(String email, String password, String clientIp) {
        loginRateLimit.check(clientIp);
        String normalized = Text.trim(email).toLowerCase(Locale.ROOT);
        Optional<User> found = users.findByEmailIgnoreCase(normalized);
        boolean passwordOk = passwordEncoder.matches(password, found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk || !found.get().isActive()) {
            String reason = found.isEmpty() || !passwordOk ? "bad_credentials" : "inactive";
            audit.append(found.map(User::getId).orElse(null), AuditType.LOGIN_FAILURE, null,
                    Map.of("email", normalized, "reason", reason, "ip", clientIp));
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        User user = found.get();
        audit.append(user.getId(), AuditType.LOGIN_SUCCESS, null, Map.of("ip", clientIp));
        return new AuthResult(response(user), issueRefreshToken(user.getId(), UUID.randomUUID()).raw());
    }

    /**
     * Rotates the presented refresh token. A token that was already rotated or revoked means it was copied: the whole
     * family is revoked so both the thief and the victim must sign in again. That revocation must survive the 401,
     * hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshToken token = tokens.findForUpdate(Hashing.sha256Hex(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (token.getRevokedAt() != null) {
            tokens.revokeFamily(token.getFamilyId(), now);
            if (token.getReplacedBy() == null) {
                // Revoked by logout or by an earlier reuse detection: dead, but not evidence of copying.
                throw new InvalidRefreshTokenException();
            }
            audit.append(token.getUserId(), AuditType.TOKEN_REUSE_DETECTED, null,
                    Map.of("familyId", token.getFamilyId().toString()));
            throw new InvalidRefreshTokenException();
        }
        Optional<User> user = users.findById(token.getUserId()).filter(User::isActive);
        if (!token.getExpiresAt().isAfter(now) || user.isEmpty()) {
            tokens.revokeFamily(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        IssuedToken next = issueRefreshToken(token.getUserId(), token.getFamilyId());
        token.rotatedTo(next.entity(), now);
        return new AuthResult(response(user.get()), next.raw());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokens.findByTokenHash(Hashing.sha256Hex(rawToken))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    private AuthResponse response(User user) {
        return new AuthResponse(tokenService.issueAccessToken(user), tokenService.accessTtlSeconds(), UserDto.of(user));
    }

    private record IssuedToken(RefreshToken entity, String raw) {
    }

    private IssuedToken issueRefreshToken(UUID userId, UUID familyId) {
        String raw = Hashing.randomToken();
        Instant now = clock.instant();
        RefreshToken saved = tokens.save(new RefreshToken(userId, familyId, Hashing.sha256Hex(raw), now,
                now.plus(refreshTtl)));
        return new IssuedToken(saved, raw);
    }
}
