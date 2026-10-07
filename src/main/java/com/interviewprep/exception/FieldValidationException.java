package com.interviewprep.exception;

/**
 * A request field that passed Bean Validation but violates a rule only the service can check. Mapped to 400 with the
 * same {@code errors} array as Bean Validation failures by {@link GlobalExceptionHandler}.
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
