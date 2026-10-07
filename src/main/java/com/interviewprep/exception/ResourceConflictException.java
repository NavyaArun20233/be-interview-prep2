package com.interviewprep.exception;

/** Base type for "conflicts with an existing resource" errors; mapped to 409 by {@link GlobalExceptionHandler}. */
public abstract class ResourceConflictException extends RuntimeException {

    protected ResourceConflictException(String message) {
        super(message);
    }
}
