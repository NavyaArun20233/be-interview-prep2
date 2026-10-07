package com.interviewprep.exception;

/** Base type for "resource existed but is no longer available" errors; mapped to 410 by {@link GlobalExceptionHandler}. */
public abstract class ResourceGoneException extends RuntimeException {

    protected ResourceGoneException(String message) {
        super(message);
    }
}
