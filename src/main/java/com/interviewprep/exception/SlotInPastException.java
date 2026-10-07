package com.interviewprep.exception;

import java.time.LocalDateTime;

/** A slot that has already started cannot be held; 422. */
public class SlotInPastException extends BusinessRuleViolationException {

    public SlotInPastException(LocalDateTime slotStart) {
        super("Slot " + slotStart + " has already started");
    }
}
