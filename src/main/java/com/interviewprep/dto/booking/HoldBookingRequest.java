package com.interviewprep.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Hold one slot. {@code slotStart} is the slot's start in clinic-local time (ISO, e.g. {@code 2026-10-08T10:30}); grid
 * alignment, clinic hours and "not in the past" are checked by the service.
 */
public record HoldBookingRequest(
        @NotNull @Positive Long doctorId,
        @NotNull LocalDateTime slotStart,
        @NotBlank @Size(max = 100) String patientName) {}
