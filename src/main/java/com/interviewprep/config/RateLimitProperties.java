package com.interviewprep.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Per-client request quota ({@code app.rate-limit.*}, env {@code APP_RATELIMIT_LIMIT} / {@code APP_RATELIMIT_WINDOW}).
 * Validated at startup so a zero limit or a sub-second window fails fast instead of silently blocking every request.
 *
 * @param limit maximum number of requests one API key may make within any {@code window}
 * @param window length of the sliding window, e.g. {@code 1m} or {@code 30s}
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("10") @Positive int limit,

        @DefaultValue("1m") @NotNull @DurationMin(seconds = 1)
        Duration window) {}
