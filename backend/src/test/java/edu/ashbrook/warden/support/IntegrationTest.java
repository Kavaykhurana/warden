package edu.ashbrook.warden.support;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import edu.ashbrook.warden.ai.ToolEventCollector;
import edu.ashbrook.warden.ai.ToolKeys;
import edu.ashbrook.warden.auth.TokenService;
import edu.ashbrook.warden.user.UserRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full application against a throwaway PostgreSQL 16 with the demo data seeded. Each test class gets a fresh
 * context (and so a fresh, freshly seeded database), so classes never see each other's changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class IntegrationTest {

    public static final String PRIYA = "priya.admin@ashbrook.edu";
    public static final String VIKRAM = "vikram.admin@ashbrook.edu";
    public static final String SUNITA = "sunita.hod@ashbrook.edu";
    public static final String RAKESH = "rakesh.hod@ashbrook.edu";
    public static final String MEERA = "meera.faculty@ashbrook.edu";
    public static final String ARJUN = "arjun.faculty@ashbrook.edu";
    public static final String ROHAN = "rohan.faculty@ashbrook.edu";
    public static final String AARAV = "aarav.student@ashbrook.edu";
    public static final String ISHA = "isha.student@ashbrook.edu";
    public static final String KABIR = "kabir.student@ashbrook.edu";
    public static final String ANANYA = "ananya.student@ashbrook.edu";
    public static final String PASSWORD = "Warden@2026";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected StubChatModel model;
    @Autowired
    private TokenService tokens;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    protected UUID id(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    protected String token(String email) {
        return tokens.issueAccessToken(users.findByEmailIgnoreCase(email).orElseThrow());
    }

    /** A real signed access token for the user, sent as a bearer header. */
    protected RequestPostProcessor as(String email) {
        String token = token(email);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    protected static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** The same authentication the resource server builds from a bearer token. */
    protected Authentication authentication(String email) {
        return jwtAuthenticationConverter.convert(jwtDecoder.decode(token(email)));
    }

    protected <T> T runAs(String email, Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(authentication(email));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    protected ToolContext toolContext(String email) {
        return new ToolContext(Map.of(ToolKeys.AUTH, authentication(email), ToolKeys.EVENTS, new ToolEventCollector()));
    }

    protected String body(Object value) {
        return json.writeValueAsString(value);
    }

    protected long auditCount(String type) {
        Long n = jdbc.queryForObject("select count(*) from audit_events where event_type = ?", Long.class, type);
        return n == null ? 0 : n;
    }
}
