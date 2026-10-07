package com.interviewprep.dto.booking;

import java.time.LocalDateTime;

/** One bookable slot, in clinic-local time. */
public record SlotResponse(LocalDateTime start, LocalDateTime end) {}
