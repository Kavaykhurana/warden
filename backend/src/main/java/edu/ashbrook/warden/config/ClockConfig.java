package edu.ashbrook.warden.config;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Every business date ("today", leave windows, timestamps in responses) is evaluated in this zone. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }

    /** Contract timestamps: ISO-8601 with the +05:30 offset, second precision. */
    public static OffsetDateTime ist(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS);
    }
}
