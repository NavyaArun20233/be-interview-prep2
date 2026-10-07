package com.interviewprep.service;

import java.time.LocalDateTime;

/** Published inside the confirm transaction; delivered to listeners only if that transaction commits. */
public record BookingConfirmedEvent(long bookingId, long doctorId, LocalDateTime slotStart) {}
