package com.interviewprep.dto.booking;

import java.time.LocalDate;
import java.util.List;

/** A doctor's free slots on one day. Bounded by clinic hours (16 slots by default), so not paginated. */
public record AvailableSlotsResponse(long doctorId, LocalDate date, List<SlotResponse> slots) {}
