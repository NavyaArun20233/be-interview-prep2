package com.interviewprep.exception;

/** An uploaded file exceeds the configured size limit; mapped to 413 by {@link GlobalExceptionHandler}. */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
