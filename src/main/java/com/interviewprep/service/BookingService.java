package com.interviewprep.service;

import com.interviewprep.config.BookingProperties;
import com.interviewprep.dto.booking.AvailableSlotsResponse;
import com.interviewprep.dto.booking.BookingResponse;
import com.interviewprep.dto.booking.HoldBookingRequest;
import com.interviewprep.dto.booking.SlotResponse;
import com.interviewprep.entity.Booking;
import com.interviewprep.entity.BookingStatus;
import com.interviewprep.exception.BookingNotFoundException;
import com.interviewprep.exception.BookingStateConflictException;
import com.interviewprep.exception.DoctorNotFoundException;
import com.interviewprep.exception.FieldValidationException;
import com.interviewprep.exception.HoldExpiredException;
import com.interviewprep.exception.SlotInPastException;
import com.interviewprep.exception.SlotUnavailableException;
import com.interviewprep.repository.BookingRepository;
import com.interviewprep.repository.DoctorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step booking: hold a slot, then confirm it before the hold runs out.
 *
 * <p>The database is the arbiter of "one active booking per slot": the partial unique index
 * {@code uq_bookings_active_slot} covers HELD and CONFIRMED rows, so concurrent holds for one slot are serialized by
 * PostgreSQL and exactly one insert wins. An overdue hold is expired in the same transaction right before the insert,
 * so it never blocks a new hold even if the housekeeping sweep has not run. State transitions on an existing booking
 * (confirm, cancel, expire) are guarded by its {@code @Version}.
 *
 * <p>Slot times are clinic-local at the API and absolute instants in the database; the clinic zone is the zone of the
 * application {@link Clock} (see {@code ClockConfig}).
 */
@Service
public class BookingService {

    static final String ACTIVE_SLOT_CONSTRAINT = "uq_bookings_active_slot";

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final DoctorRepository doctorRepository;
    private final BookingProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(
            BookingRepository bookingRepository,
            DoctorRepository doctorRepository,
            BookingProperties properties,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.doctorRepository = doctorRepository;
        this.properties = properties;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public AvailableSlotsResponse availableSlots(long doctorId, LocalDate date) {
        requireDoctor(doctorId);
        Instant now = now();
        ZoneId zone = clock.getZone();
        Instant from = date.atTime(properties.dayStart()).atZone(zone).toInstant();
        Instant to = date.atTime(properties.dayEnd()).atZone(zone).toInstant();
        // Overdue holds are not returned here, so they count as free even before the sweep marks them EXPIRED.
        Set<Instant> taken = new HashSet<>(bookingRepository.findTakenSlotStarts(doctorId, from, to, now));

        List<SlotResponse> slots = properties.slotStarts().stream()
                .map(date::atTime)
                .filter(start -> {
                    Instant instant = start.atZone(zone).toInstant();
                    return instant.isAfter(now) && !taken.contains(instant);
                })
                .map(start -> new SlotResponse(start, start.plus(properties.slotLength())))
                .toList();
        return new AvailableSlotsResponse(doctorId, date, slots);
    }

    @Transactional
    public BookingResponse hold(HoldBookingRequest request) {
        long doctorId = request.doctorId();
        LocalDateTime slotStart = request.slotStart();
        requireOnGrid(slotStart);
        requireDoctor(doctorId);
        Instant now = now();
        Instant slotInstant = slotStart.atZone(clock.getZone()).toInstant();
        if (!slotInstant.isAfter(now)) {
            throw new SlotInPastException(slotStart);
        }

        // Release an overdue hold on this slot first; the insert below then only conflicts with a live booking.
        bookingRepository.expireOverdueHold(doctorId, slotInstant, now);
        Booking booking =
                Booking.hold(doctorId, slotInstant, request.patientName().strip(), now, properties.holdDuration());
        try {
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            if (isActiveSlotViolation(ex)) {
                throw new SlotUnavailableException(doctorId, slotStart);
            }
            throw ex;
        }
        log.info(
                "Booking {} holds slot {} of doctor {} until {}",
                booking.getId(),
                slotStart,
                doctorId,
                booking.getHoldExpiresAt());
        return toResponse(booking);
    }

    /**
     * Confirms a live hold. An overdue hold is marked EXPIRED (committed despite the 410) and cannot be confirmed. The
     * confirmation notification is published here but only delivered after this transaction commits.
     */
    @Transactional(noRollbackFor = HoldExpiredException.class)
    public BookingResponse confirm(long id) {
        Instant now = now();
        Booking booking = findBooking(id);
        requireLiveHold(booking, now, "confirmed");
        booking.confirm(now);
        // Surfaces a lost race with cancel/expiry (version conflict -> 409) before the event is published.
        bookingRepository.flush();
        eventPublisher.publishEvent(new BookingConfirmedEvent(
                booking.getId(),
                booking.getDoctorId(),
                LocalDateTime.ofInstant(booking.getSlotStart(), clock.getZone())));
        log.info("Booking {} confirmed", booking.getId());
        return toResponse(booking);
    }

    /** Cancels a confirmed booking or a live hold; either way the slot becomes free again. */
    @Transactional(noRollbackFor = HoldExpiredException.class)
    public BookingResponse cancel(long id) {
        Instant now = now();
        Booking booking = findBooking(id);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            requireLiveHold(booking, now, "cancelled");
        }
        booking.cancel(now);
        bookingRepository.flush();
        log.info("Booking {} cancelled", booking.getId());
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(long id) {
        return toResponse(findBooking(id));
    }

    /** Housekeeping: marks every overdue hold EXPIRED. Reads and holds never depend on this having run. */
    @Transactional
    public int expireOverdueHolds() {
        int expired = bookingRepository.expireOverdueHolds(now());
        if (expired > 0) {
            log.info("Expired {} overdue booking holds", expired);
        }
        return expired;
    }

    private void requireLiveHold(Booking booking, Instant now, String action) {
        if (booking.isHoldOverdue(now)) {
            booking.expire(now);
            throw new HoldExpiredException(booking.getId());
        }
        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new HoldExpiredException(booking.getId());
        }
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new BookingStateConflictException(booking.getId(), booking.getStatus(), action);
        }
    }

    private void requireOnGrid(LocalDateTime slotStart) {
        LocalTime time = slotStart.toLocalTime();
        if (!properties.isSlotStart(time)) {
            throw new FieldValidationException(
                    "slotStart",
                    "must be the start of a " + properties.slotLength().toMinutes() + "-minute slot between "
                            + properties.dayStart() + " and " + properties.dayEnd());
        }
    }

    private void requireDoctor(long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new DoctorNotFoundException(doctorId);
        }
    }

    private Booking findBooking(long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    /** Microsecond precision, matching what PostgreSQL stores, so responses agree with later reads. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private BookingResponse toResponse(Booking booking) {
        return BookingResponse.from(booking, clock.getZone(), properties.slotLength());
    }

    private static boolean isActiveSlotViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return ACTIVE_SLOT_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
