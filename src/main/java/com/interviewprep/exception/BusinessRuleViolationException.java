package com.interviewprep.exception;

/** Base type for "valid request that breaks a business rule" errors; mapped to 422 by {@link GlobalExceptionHandler}. */
public abstract class BusinessRuleViolationException extends RuntimeException {

    protected BusinessRuleViolationException(String message) {
        super(message);
    }
}
