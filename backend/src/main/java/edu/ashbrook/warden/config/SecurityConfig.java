package edu.ashbrook.warden.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import edu.ashbrook.warden.auth.ActiveUserFilter;
import edu.ashbrook.warden.common.JsonAccessDeniedHandler;
import edu.ashbrook.warden.common.JsonAuthEntryPoint;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                    ActiveUserFilter activeUserFilter, JsonAuthEntryPoint entryPoint,
                                    JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // Stateless bearer-token API: no session, and no cookie authenticates an API call (see THREAT_MODEL.md).
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health", "/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(activeUserFilter, BearerTokenAuthenticationFilter.class)
                .headers(headers -> headers.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                        new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))));
        return http.build();
    }

    /** The filter runs inside the security chain only; stop Boot from also registering it as a servlet filter. */
    @Bean
    FilterRegistrationBean<ActiveUserFilter> activeUserFilterRegistration(ActiveUserFilter filter) {
        FilterRegistrationBean<ActiveUserFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
