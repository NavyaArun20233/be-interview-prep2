package com.interviewprep.exception;

import java.time.LocalDateTime;

/** The slot is already held or booked by someone else; 409. */
public class SlotUnavailableException extends ResourceConflictException {

    public SlotUnavailableException(long doctorId, LocalDateTime slotStart) {
        super("Slot " + slotStart + " of doctor " + doctorId + " is not available");
    }
}
