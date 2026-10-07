package com.interviewprep.exception;

/** Unknown doctor id; 404. */
public class DoctorNotFoundException extends ResourceNotFoundException {

    public DoctorNotFoundException(long id) {
        super("Doctor " + id + " not found");
    }
}
