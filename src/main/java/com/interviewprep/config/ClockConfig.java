package com.interviewprep.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single source of "now" so time-dependent logic can be tested with a fixed clock. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * Business time zone: decides what "today" is (e.g. for {@code @FutureOrPresent} due dates). Stored timestamps are
     * {@code Instant}s and are unaffected by the zone.
     */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
