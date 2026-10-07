package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewprep.config.BookingProperties;
import com.interviewprep.dto.booking.AvailableSlotsResponse;
import com.interviewprep.dto.booking.BookingResponse;
import com.interviewprep.dto.booking.HoldBookingRequest;
import com.interviewprep.dto.booking.SlotResponse;
import com.interviewprep.entity.Booking;
import com.interviewprep.entity.BookingStatus;
import com.interviewprep.exception.BookingStateConflictException;
import com.interviewprep.exception.DoctorNotFoundException;
import com.interviewprep.exception.FieldValidationException;
import com.interviewprep.exception.HoldExpiredException;
import com.interviewprep.exception.SlotInPastException;
import com.interviewprep.exception.SlotUnavailableException;
import com.interviewprep.repository.BookingRepository;
import com.interviewprep.repository.DoctorRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class BookingServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** 2026-10-07 10:00 in the clinic. */
    private static final Instant NOW = Instant.parse("2026-10-07T04:30:00Z");

    private static final long DOCTOR_ID = 3L;
    private static final LocalDateTime SLOT = LocalDateTime.parse("2026-10-08T10:30");
    private static final Instant SLOT_INSTANT = SLOT.atZone(ZONE).toInstant();

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final BookingProperties properties = new BookingProperties(
            Duration.ofMinutes(30), Duration.ofMinutes(5), LocalTime.of(9, 0), LocalTime.of(17, 0), null);

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(
                bookingRepository, doctorRepository, properties, Clock.fixed(NOW, ZONE), eventPublisher);
        when(doctorRepository.existsById(DOCTOR_ID)).thenReturn(true);
    }

    @Test
    void holdExpiresAnOverdueHoldBeforeInsertingTheNewOne() {
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        BookingResponse response = service.hold(new HoldBookingRequest(DOCTOR_ID, SLOT, "  Alice "));

        InOrder order = inOrder(bookingRepository);
        order.verify(bookingRepository).expireOverdueHold(DOCTOR_ID, SLOT_INSTANT, NOW);
        order.verify(bookingRepository).saveAndFlush(any(Booking.class));
        assertThat(response.status()).isEqualTo(BookingStatus.HELD);
        assertThat(response.patientName()).isEqualTo("Alice");
        assertThat(response.slotStart()).isEqualTo(SLOT);
        assertThat(response.slotEnd()).isEqualTo(SLOT.plusMinutes(30));
        assertThat(response.holdExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void holdTranslatesTheActiveSlotIndexViolationTo409() {
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(violation(BookingService.ACTIVE_SLOT_CONSTRAINT));

        assertThatThrownBy(() -> service.hold(new HoldBookingRequest(DOCTOR_ID, SLOT, "Alice")))
                .isInstanceOf(SlotUnavailableException.class)
                .hasMessage("Slot 2026-10-08T10:30 of doctor 3 is not available");
    }

    @Test
    void holdRethrowsOtherIntegrityViolations() {
        DataIntegrityViolationException other = violation("bookings_doctor_id_fkey");
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenThrow(other);

        assertThatThrownBy(() -> service.hold(new HoldBookingRequest(DOCTOR_ID, SLOT, "Alice")))
                .isSameAs(other);
    }

    @Test
    void holdRejectsSlotsOffTheGridOrOutsideClinicHours() {
        for (String slot : List.of("2026-10-08T10:15", "2026-10-08T10:30:01", "2026-10-08T08:30", "2026-10-08T17:00")) {
            assertThatThrownBy(() -> service.hold(new HoldBookingRequest(DOCTOR_ID, LocalDateTime.parse(slot), "A")))
                    .as(slot)
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("30-minute slot between 09:00 and 17:00");
        }
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void holdRejectsSlotsThatHaveStarted() {
        LocalDateTime started = LocalDateTime.parse("2026-10-07T10:00");

        assertThatThrownBy(() -> service.hold(new HoldBookingRequest(DOCTOR_ID, started, "Alice")))
                .isInstanceOf(SlotInPastException.class);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void holdRejectsUnknownDoctor() {
        assertThatThrownBy(() -> service.hold(new HoldBookingRequest(99L, SLOT, "Alice")))
                .isInstanceOf(DoctorNotFoundException.class);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void confirmPublishesTheEventAfterFlushing() {
        Booking booking = stored(heldAt(NOW.minus(Duration.ofMinutes(4))));

        BookingResponse response = service.confirm(7L);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.confirmedAt()).isEqualTo(NOW);
        InOrder order = inOrder(bookingRepository, eventPublisher);
        order.verify(bookingRepository).flush();
        order.verify(eventPublisher).publishEvent(new BookingConfirmedEvent(7L, DOCTOR_ID, SLOT));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmOfAnOverdueHoldMarksItExpiredAndFailsWithGone() {
        Booking booking = stored(heldAt(NOW.minus(Duration.ofMinutes(5))));

        assertThatThrownBy(() -> service.confirm(7L)).isInstanceOf(HoldExpiredException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void confirmOfACancelledBookingConflicts() {
        Booking booking = heldAt(NOW);
        booking.cancel(NOW);
        stored(booking);

        assertThatThrownBy(() -> service.confirm(7L))
                .isInstanceOf(BookingStateConflictException.class)
                .hasMessage("Booking 7 is CANCELLED and cannot be confirmed");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void cancelFreesAConfirmedBookingEvenAfterTheHoldWindow() {
        Booking booking = heldAt(NOW.minus(Duration.ofHours(1)));
        booking.confirm(NOW.minus(Duration.ofMinutes(59)));
        stored(booking);

        assertThat(service.cancel(7L).status()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).flush();
    }

    @Test
    void cancelOfAnOverdueHoldFailsWithGone() {
        Booking booking = stored(heldAt(NOW.minus(Duration.ofMinutes(6))));

        assertThatThrownBy(() -> service.cancel(7L)).isInstanceOf(HoldExpiredException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void availableSlotsExcludeTakenAndStartedSlots() {
        LocalDate today = LocalDate.of(2026, 10, 7);
        Instant taken = LocalDateTime.parse("2026-10-07T11:00").atZone(ZONE).toInstant();
        when(bookingRepository.findTakenSlotStarts(anyLong(), any(), any(), any()))
                .thenReturn(List.of(taken));

        AvailableSlotsResponse response = service.availableSlots(DOCTOR_ID, today);

        // 09:00-10:00 started (it is 10:00), 11:00 taken: 16 - 3 - 1 slots remain.
        assertThat(response.slots()).hasSize(12);
        assertThat(response.slots().getFirst())
                .isEqualTo(new SlotResponse(
                        LocalDateTime.parse("2026-10-07T10:30"), LocalDateTime.parse("2026-10-07T11:00")));
        assertThat(response.slots())
                .extracting(SlotResponse::start)
                .doesNotContain(LocalDateTime.parse("2026-10-07T11:00"));
        verify(bookingRepository)
                .findTakenSlotStarts(
                        DOCTOR_ID,
                        today.atTime(9, 0).atZone(ZONE).toInstant(),
                        today.atTime(17, 0).atZone(ZONE).toInstant(),
                        NOW);
    }

    @Test
    void availableSlotsOfUnknownDoctorIsNotFound() {
        assertThatThrownBy(() -> service.availableSlots(99L, LocalDate.of(2026, 10, 8)))
                .isInstanceOf(DoctorNotFoundException.class);
    }

    private Booking heldAt(Instant heldAt) {
        return Booking.hold(DOCTOR_ID, SLOT_INSTANT, "Alice", heldAt, properties.holdDuration());
    }

    private Booking stored(Booking booking) {
        ReflectionTestUtils.setField(booking, "id", 7L);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        return booking;
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException(
                "duplicate", new ConstraintViolationException("duplicate", new SQLException("23505"), constraint));
    }
}
