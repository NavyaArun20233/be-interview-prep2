package com.interviewprep.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Booking ITs: a controllable clock (hold expiry without sleeping) and shared data helpers. */
@TestConfiguration(proxyBeanMethods = false)
public class BookingTestConfiguration {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    /** 2026-10-07 10:00 in the clinic: the tests book slots on the next day. */
    static final Instant START = Instant.parse("2026-10-07T04:30:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(START, CLINIC_ZONE);
    }

    static long insertDoctor(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO doctors (name, specialty) VALUES ('Dr. Test', 'Testing') RETURNING id", Long.class);
    }

    static void deleteBookingsAndTestDoctors(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM bookings");
        jdbcTemplate.update("DELETE FROM doctors WHERE name = 'Dr. Test'");
    }

    static String holdBody(long doctorId, String slotStart, String patientName) {
        return """
                {"doctorId": %d, "slotStart": "%s", "patientName": "%s"}""".formatted(doctorId, slotStart, patientName);
    }

    /** A clock that only moves when a test moves it. Thread-safe: requests read it on server threads. */
    public static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this(new AtomicReference<>(instant), zone);
        }

        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        public void set(Instant value) {
            instant.set(value);
        }

        public void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(instant, newZone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
