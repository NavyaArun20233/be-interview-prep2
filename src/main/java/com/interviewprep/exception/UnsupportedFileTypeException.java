package com.interviewprep.exception;

/**
 * An uploaded file's content is not an allowed type, or its extension does not match its content; mapped to 415 by
 * {@link GlobalExceptionHandler}.
 */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
