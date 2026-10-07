package com.interviewprep.exception;

import com.interviewprep.entity.BookingStatus;

/** The booking is in a state that does not allow the requested transition (e.g. confirming a cancelled booking); 409. */
public class BookingStateConflictException extends ResourceConflictException {

    public BookingStateConflictException(long id, BookingStatus status, String action) {
        super("Booking " + id + " is " + status + " and cannot be " + action);
    }
}
