package com.interviewprep.dto.booking;

import com.interviewprep.entity.Booking;
import com.interviewprep.entity.BookingStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A booking. Slot times are clinic-local; {@code holdExpiresAt}, {@code confirmedAt}, {@code createdAt} are instants. */
public record BookingResponse(
        long id,
        long doctorId,
        LocalDateTime slotStart,
        LocalDateTime slotEnd,
        String patientName,
        BookingStatus status,
        Instant holdExpiresAt,
        Instant confirmedAt,
        Instant createdAt) {

    public static BookingResponse from(Booking booking, ZoneId zone, Duration slotLength) {
        LocalDateTime start = LocalDateTime.ofInstant(booking.getSlotStart(), zone);
        return new BookingResponse(
                booking.getId(),
                booking.getDoctorId(),
                start,
                start.plus(slotLength),
                booking.getPatientName(),
                booking.getStatus(),
                booking.getHoldExpiresAt(),
                booking.getConfirmedAt(),
                booking.getCreatedAt());
    }
}
