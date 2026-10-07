package com.interviewprep.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    record Dated(@FutureOrPresent LocalDate date) {}

    @Test
    void clockUsesIndiaTimeZone() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void todayIsJudgedInBusinessZone() {
        // 2026-10-07T20:00Z is 2026-10-08 01:30 in India: the 7th is already past, the 8th is today.
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ClockConfig.BUSINESS_ZONE);
        try (ValidatorFactory factory = Validation.byDefaultProvider()
                .configure()
                .clockProvider(() -> clock)
                .buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(new Dated(LocalDate.of(2026, 10, 7))))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("date");
            assertThat(validator.validate(new Dated(LocalDate.of(2026, 10, 8)))).isEmpty();
        }
    }
}
