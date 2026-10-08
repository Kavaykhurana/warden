package edu.ashbrook.warden.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import edu.ashbrook.warden.support.IntegrationTest;

class AuthIT extends IntegrationTest {

    private static final Pattern REFRESH = Pattern.compile("warden_rt=([^;]*)");

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").with(fromIp(ip)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))));
    }

    private static String refreshCookie(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).isNotNull();
        Matcher m = REFRESH.matcher(header);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ResultActions refresh(String cookie) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(new Cookie("warden_rt", cookie)));
    }

    @Test
    void loginReturnsTokenUserAndHardenedRefreshCookie() throws Exception {
        MvcResult result = login(AARAV, PASSWORD, "10.0.0.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(AARAV))
                .andExpect(jsonPath("$.user.rollNo").value("102303011"))
                .andExpect(jsonPath("$.user.mentorName").value("Dr. Meera Iyer"))
                .andReturn();
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        String accessToken = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aarav Sharma"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswerAndAreAudited() throws Exception {
        long before = auditCount("LOGIN_FAILURE");
        login(AARAV, "not-the-password", "10.0.0.2")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
        login("nobody@ashbrook.edu", PASSWORD, "10.0.0.2")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
        assertThat(auditCount("LOGIN_FAILURE")).isEqualTo(before + 2);
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        String first = refreshCookie(login(ISHA, PASSWORD, "10.0.0.3").andReturn());
        String second = refreshCookie(refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(ISHA)).andReturn());
        assertThat(second).isNotEqualTo(first);
        refresh(second).andExpect(status().isOk());
        refresh("").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamilyAndIsAudited() throws Exception {
        long before = auditCount("TOKEN_REUSE_DETECTED");
        String stolen = refreshCookie(login(KABIR, PASSWORD, "10.0.0.4").andReturn());
        String current = refreshCookie(refresh(stolen).andExpect(status().isOk()).andReturn());

        refresh(stolen).andExpect(status().isUnauthorized());
        // The legitimate holder's newer token died with the family.
        refresh(current).andExpect(status().isUnauthorized());
        assertThat(auditCount("TOKEN_REUSE_DETECTED")).isEqualTo(before + 1);
    }

    @Test
    void logoutRevokesTheFamilyAndClearsTheCookie() throws Exception {
        String cookie = refreshCookie(login(ANANYA, PASSWORD, "10.0.0.5").andReturn());
        MvcResult result = mvc.perform(post("/api/auth/logout").cookie(new Cookie("warden_rt", cookie)))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void sixthLoginAttemptInAMinuteFromOneIpIsRateLimited() throws Exception {
        long before = auditCount("RATE_LIMITED");
        for (int i = 0; i < 5; i++) {
            login(MEERA, "wrong-" + i, "10.0.0.6").andExpect(status().isUnauthorized());
        }
        login(MEERA, PASSWORD, "10.0.0.6")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        login(MEERA, PASSWORD, "10.0.0.7").andExpect(status().isOk());
        assertThat(auditCount("RATE_LIMITED")).isEqualTo(before + 1);
    }

    @Test
    void deactivationRejectsTheUsersLiveAccessTokenImmediately() throws Exception {
        String rohanToken = token(ROHAN);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + rohanToken)).andExpect(status().isOk());

        String pendingId = jdbc.queryForObject("select id::text from action_requests where tool_name = 'deactivateUser' "
                + "and status = 'PENDING'", String.class);
        mvc.perform(post("/api/approvals/" + pendingId + "/approve").with(as(VIKRAM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        // Still well inside both the token's 15 minutes and the 30-second activity cache.
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + rohanToken))
                .andExpect(status().isUnauthorized());
        login(ROHAN, PASSWORD, "10.0.0.8").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }
}
