package com.interviewprep.exception;

/** Unknown booking id; 404. */
public class BookingNotFoundException extends ResourceNotFoundException {

    public BookingNotFoundException(long id) {
        super("Booking " + id + " not found");
    }
}
