package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.integration.BookingTestConfiguration.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Many patients holding the same slot at the same moment, through HTTP and real PostgreSQL. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.booking.expiry-sweep.enabled=false")
@Import({TestcontainersConfiguration.class, BookingTestConfiguration.class})
class BookingConcurrencyIT {

    private static final int PATIENTS = 20;
    private static final String SLOT = "2026-10-08T10:00:00";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    private RestTestClient client;
    private long doctorId;

    @BeforeEach
    void setUp() {
        clock.set(BookingTestConfiguration.START);
        doctorId = BookingTestConfiguration.insertDoctor(jdbcTemplate);
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void cleanUp() {
        BookingTestConfiguration.deleteBookingsAndTestDoctors(jdbcTemplate);
    }

    /** Acceptance: 20 patients hold the same slot at the same moment - exactly one succeeds. */
    @Test
    void simultaneousHoldsOfOneSlotHaveExactlyOneWinner() throws Exception {
        List<Integer> statuses = holdConcurrently();

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(PATIENTS - 1);
        assertThat(activeBookings()).isEqualTo(1);
    }

    /** The expire-then-insert path under contention: an overdue hold is replaced by exactly one new hold. */
    @Test
    void simultaneousHoldsOfAnExpiredHoldsSlotHaveExactlyOneWinner() throws Exception {
        client.post()
                .uri("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BookingTestConfiguration.holdBody(doctorId, SLOT, "first-patient"))
                .exchange()
                .expectStatus()
                .isCreated();
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        List<Integer> statuses = holdConcurrently();

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(PATIENTS - 1);
        assertThat(activeBookings()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM bookings WHERE patient_name = 'first-patient'", String.class))
                .isEqualTo("EXPIRED");
    }

    private Integer activeBookings() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE doctor_id = ? AND status IN ('HELD', 'CONFIRMED')",
                Integer.class,
                doctorId);
    }

    /** Each patient holds {@link #SLOT}; all requests are released together by a start gate to maximize contention. */
    private List<Integer> holdConcurrently() throws Exception {
        CountDownLatch ready = new CountDownLatch(PATIENTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(PATIENTS);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < PATIENTS; i++) {
                String patient = "patient-" + i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    // Status only: a 409 body is Problem Details, not a booking.
                    return client.post()
                            .uri("/api/v1/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BookingTestConfiguration.holdBody(doctorId, SLOT, patient))
                            .exchange()
                            .returnResult(Void.class)
                            .getStatus()
                            .value();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }
}
