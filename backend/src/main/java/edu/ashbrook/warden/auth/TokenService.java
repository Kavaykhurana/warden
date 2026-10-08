package edu.ashbrook.warden.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import edu.ashbrook.warden.user.User;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration accessTtl;

    public TokenService(JwtEncoder encoder, Clock clock, @Value("${warden.jwt.access-ttl}") Duration accessTtl) {
        this.encoder = encoder;
        this.clock = clock;
        this.accessTtl = accessTtl;
    }

    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim("roles", List.of(user.getRole().name()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName());
        if (user.getDepartmentCode() != null) {
            claims.claim("dept", user.getDepartmentCode());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
