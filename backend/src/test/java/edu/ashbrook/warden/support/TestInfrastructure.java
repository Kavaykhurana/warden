package edu.ashbrook.warden.support;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

import edu.ashbrook.warden.config.ClockConfig;

@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    /** The demo date: Thursday 8 October 2026, 10:00 in Asia/Kolkata. */
    public static final Instant NOW = Instant.parse("2026-10-08T04:30:00Z");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16");
    }

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(NOW, ClockConfig.ZONE);
    }

    /** Replaced by the real Gemini model only in live tests, which set {@code spring.ai.model.chat=google-genai}. */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none")
    StubChatModel stubChatModel() {
        return new StubChatModel();
    }
}
