package edu.ashbrook.warden.auth;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import edu.ashbrook.warden.common.ApiError;
import edu.ashbrook.warden.common.ErrorCode;
import edu.ashbrook.warden.user.UserDto;
import edu.ashbrook.warden.user.UserService;

@RestController
public class AuthController {

    static final String COOKIE = "warden_rt";

    public record LoginRequest(
            @NotBlank(message = "Enter your email.") @Size(max = 255, message = "That email is too long.") String email,
            @NotBlank(message = "Enter your password.") @Size(max = 64, message = "That password is too long.") String password) {
    }

    private final AuthService authService;
    private final UserService userService;
    private final boolean cookieSecure;
    private final Duration refreshTtl;

    public AuthController(AuthService authService, UserService userService,
                          @Value("${warden.refresh.cookie-secure}") boolean cookieSecure,
                          @Value("${warden.refresh.ttl}") Duration refreshTtl) {
        this.authService = authService;
        this.userService = userService;
        this.cookieSecure = cookieSecure;
        this.refreshTtl = refreshTtl;
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        AuthService.AuthResult result = authService.login(body.email(), body.password(), request.getRemoteAddr());
        return withCookie(result);
    }

    @PostMapping("/api/auth/refresh")
    ResponseEntity<?> refresh(@CookieValue(name = COOKIE, required = false) String refreshToken) {
        try {
            return withCookie(authService.refresh(refreshToken));
        } catch (InvalidRefreshTokenException e) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                    .body(ApiError.of(401, ErrorCode.UNAUTHORIZED, e.getMessage()));
        }
    }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @GetMapping("/api/me")
    UserDto me() {
        return userService.me();
    }

    private ResponseEntity<AuthResponse> withCookie(AuthService.AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), refreshTtl).toString())
                .body(result.response());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
