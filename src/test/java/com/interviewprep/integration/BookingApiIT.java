package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.dto.booking.AvailableSlotsResponse;
import com.interviewprep.dto.booking.BookingResponse;
import com.interviewprep.dto.booking.SlotResponse;
import com.interviewprep.entity.BookingStatus;
import com.interviewprep.integration.BookingTestConfiguration.MutableClock;
import com.interviewprep.service.BookingConfirmedEvent;
import com.interviewprep.service.BookingNotifier;
import com.interviewprep.service.BookingService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Booking lifecycle through HTTP against PostgreSQL (V5 schema), with a controllable clock. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.booking.expiry-sweep.enabled=false")
@Import({TestcontainersConfiguration.class, BookingTestConfiguration.class})
class BookingApiIT {

    private static final String DATE = "2026-10-08";
    private static final String SLOT = DATE + "T10:00:00";
    private static final LocalDateTime SLOT_TIME = LocalDateTime.parse(SLOT);
    private static final Duration PAST_HOLD = Duration.ofMinutes(5).plusSeconds(1);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private Flyway flyway;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private BookingNotifier notifier;

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

    @Test
    void migrationCreatesSchemaAndSeedsDoctors() {
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("5");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM doctors WHERE name <> 'Dr. Test'", Integer.class))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void listsEveryThirtyMinuteSlotOfTheDay() {
        AvailableSlotsResponse slots = slots(DATE);

        assertThat(slots.doctorId()).isEqualTo(doctorId);
        assertThat(slots.slots()).hasSize(16);
        assertThat(slots.slots().getFirst())
                .isEqualTo(
                        new SlotResponse(LocalDateTime.parse(DATE + "T09:00"), LocalDateTime.parse(DATE + "T09:30")));
        assertThat(slots.slots().getLast())
                .isEqualTo(
                        new SlotResponse(LocalDateTime.parse(DATE + "T16:30"), LocalDateTime.parse(DATE + "T17:00")));
    }

    @Test
    void todayListsOnlySlotsThatHaveNotStarted() {
        // The clock is 2026-10-07 10:00 in the clinic: 10:00 has started, 10:30 is the first free slot.
        AvailableSlotsResponse slots = slots("2026-10-07");

        assertThat(slots.slots()).hasSize(13);
        assertThat(slots.slots().getFirst().start()).isEqualTo(LocalDateTime.parse("2026-10-07T10:30"));
    }

    @Test
    void heldSlotDisappearsAndReturnsWhenTheHoldExpires() {
        BookingResponse held = hold(SLOT, "alice");
        assertThat(held.status()).isEqualTo(BookingStatus.HELD);
        assertThat(held.slotStart()).isEqualTo(SLOT_TIME);
        assertThat(held.slotEnd()).isEqualTo(SLOT_TIME.plusMinutes(30));
        assertThat(held.holdExpiresAt()).isEqualTo(BookingTestConfiguration.START.plus(Duration.ofMinutes(5)));
        assertThat(slotStarts()).doesNotContain(SLOT_TIME);
        holdRequest(SLOT, "bob").expectStatus().isEqualTo(409);

        clock.advance(PAST_HOLD);

        // Free before anything has marked the hold EXPIRED.
        assertThat(statusOf(held.id())).isEqualTo("HELD");
        assertThat(slotStarts()).contains(SLOT_TIME);
        BookingResponse rebooked = hold(SLOT, "bob");
        assertThat(rebooked.status()).isEqualTo(BookingStatus.HELD);
        assertThat(statusOf(held.id())).isEqualTo("EXPIRED");
        assertThat(slotStarts()).doesNotContain(SLOT_TIME);
    }

    @Test
    void confirmBeforeExpirySucceedsAndNotifiesAsynchronouslyAfterCommit() {
        AtomicReference<String> notifierThread = new AtomicReference<>();
        doAnswer(invocation -> {
                    notifierThread.set(Thread.currentThread().getName());
                    return invocation.callRealMethod();
                })
                .when(notifier)
                .sendConfirmation(any());
        BookingResponse held = hold(SLOT, "alice");
        clock.advance(Duration.ofMinutes(4));

        BookingResponse confirmed = post("/api/v1/bookings/" + held.id() + "/confirm")
                .expectStatus()
                .isOk()
                .returnResult(BookingResponse.class)
                .getResponseBody();

        assertThat(confirmed.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.confirmedAt()).isEqualTo(BookingTestConfiguration.START.plus(Duration.ofMinutes(4)));
        assertThat(statusOf(held.id())).isEqualTo("CONFIRMED");
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        verify(notifier).sendConfirmation(new BookingConfirmedEvent(held.id(), doctorId, SLOT_TIME)));
        assertThat(notifierThread.get()).startsWith("booking-notify-");

        // A confirmed booking keeps the slot even after the hold window.
        clock.advance(Duration.ofMinutes(10));
        assertThat(slotStarts()).doesNotContain(SLOT_TIME);
        holdRequest(SLOT, "bob").expectStatus().isEqualTo(409);
    }

    @Test
    void confirmAfterExpiryIsGoneAndReleasesTheSlot() {
        BookingResponse held = hold(SLOT, "alice");
        clock.advance(PAST_HOLD);

        post("/api/v1/bookings/" + held.id() + "/confirm")
                .expectStatus()
                .isEqualTo(410)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("The hold on booking " + held.id() + " has expired");

        assertThat(statusOf(held.id())).isEqualTo("EXPIRED");
        assertThat(slotStarts()).contains(SLOT_TIME);
        post("/api/v1/bookings/" + held.id() + "/confirm").expectStatus().isEqualTo(410);
        verify(notifier, never()).sendConfirmation(any());
    }

    @Test
    void cancellingAConfirmedBookingFreesTheSlot() {
        BookingResponse held = hold(SLOT, "alice");
        post("/api/v1/bookings/" + held.id() + "/confirm").expectStatus().isOk();

        BookingResponse cancelled = post("/api/v1/bookings/" + held.id() + "/cancel")
                .expectStatus()
                .isOk()
                .returnResult(BookingResponse.class)
                .getResponseBody();

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(slotStarts()).contains(SLOT_TIME);
        post("/api/v1/bookings/" + held.id() + "/cancel").expectStatus().isEqualTo(409);
        post("/api/v1/bookings/" + held.id() + "/confirm").expectStatus().isEqualTo(409);
        assertThat(hold(SLOT, "bob").status()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void getReturnsTheBookingAtItsLocation() {
        String location = holdRequest(SLOT, "alice")
                .expectStatus()
                .isCreated()
                .returnResult(BookingResponse.class)
                .getResponseHeaders()
                .getLocation()
                .toString();

        client.get()
                .uri(location)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("HELD")
                .jsonPath("$.slotStart")
                .isEqualTo(SLOT);
    }

    @Test
    void notificationIsNotSentWhenTheConfirmTransactionRollsBack() {
        BookingResponse rolledBack = hold(SLOT, "alice");
        BookingResponse committed = hold(DATE + "T11:00:00", "bob");

        transactionTemplate.executeWithoutResult(status -> {
            bookingService.confirm(rolledBack.id());
            status.setRollbackOnly();
        });
        post("/api/v1/bookings/" + committed.id() + "/confirm").expectStatus().isOk();

        // The committed confirm's notification proves delivery has happened; the rolled-back one never is.
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> verify(notifier).sendConfirmation(argThat(event -> event.bookingId() == committed.id())));
        verify(notifier, never()).sendConfirmation(argThat(event -> event.bookingId() == rolledBack.id()));
        assertThat(statusOf(rolledBack.id())).isEqualTo("HELD");
    }

    @Test
    void invalidHoldsAreRejected() {
        holdRequest(DATE + "T10:15:00", "alice")
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("slotStart");
        holdRequest(DATE + "T08:30:00", "alice").expectStatus().isBadRequest();
        holdRequest(DATE + "T17:00:00", "alice").expectStatus().isBadRequest();
        holdRequest(SLOT, " ").expectStatus().isBadRequest();
        // 2026-10-07 10:00 has already started (the clock is exactly 10:00).
        holdRequest("2026-10-07T10:00:00", "alice").expectStatus().isEqualTo(422);
        client.post()
                .uri("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BookingTestConfiguration.holdBody(Long.MAX_VALUE, SLOT, "alice"))
                .exchange()
                .expectStatus()
                .isNotFound();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM bookings", Integer.class))
                .isZero();
    }

    @Test
    void unknownResourcesAndBadParametersAreReported() {
        client.get()
                .uri("/api/v1/doctors/{id}/slots?date={date}", Long.MAX_VALUE, DATE)
                .exchange()
                .expectStatus()
                .isNotFound();
        client.get()
                .uri("/api/v1/doctors/{id}/slots", doctorId)
                .exchange()
                .expectStatus()
                .isBadRequest();
        client.get()
                .uri("/api/v1/doctors/{id}/slots?date=08-10-2026", doctorId)
                .exchange()
                .expectStatus()
                .isBadRequest();
        post("/api/v1/bookings/" + Long.MAX_VALUE + "/confirm").expectStatus().isNotFound();
        post("/api/v1/bookings/" + Long.MAX_VALUE + "/cancel").expectStatus().isNotFound();
    }

    private RestTestClient.ResponseSpec holdRequest(String slotStart, String patientName) {
        return client.post()
                .uri("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BookingTestConfiguration.holdBody(doctorId, slotStart, patientName))
                .exchange();
    }

    private BookingResponse hold(String slotStart, String patientName) {
        return holdRequest(slotStart, patientName)
                .expectStatus()
                .isCreated()
                .returnResult(BookingResponse.class)
                .getResponseBody();
    }

    private RestTestClient.ResponseSpec post(String uri) {
        return client.post().uri(uri).exchange();
    }

    private AvailableSlotsResponse slots(String date) {
        return client.get()
                .uri("/api/v1/doctors/{id}/slots?date={date}", doctorId, date)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(AvailableSlotsResponse.class)
                .getResponseBody();
    }

    private List<LocalDateTime> slotStarts() {
        return slots(DATE).slots().stream().map(SlotResponse::start).toList();
    }

    private String statusOf(long bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }
}
