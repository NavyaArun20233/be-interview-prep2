package com.interviewprep.exception;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 429 with a {@code Retry-After} header and a {@code retryAfterSeconds} Problem Details property. As an
 * {@link ErrorResponseException} it is rendered by {@link GlobalExceptionHandler} without a dedicated handler.
 */
public class RateLimitExceededException extends ErrorResponseException {

    static final String RETRY_AFTER_PROPERTY = "retryAfterSeconds";

    public RateLimitExceededException(int limit, Duration window, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, body(limit, window, retryAfterSeconds), null);
        getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    }

    private static ProblemDetail body(int limit, Duration window, long retryAfterSeconds) {
        String detail = "Rate limit of %d requests per %d seconds exceeded; retry after %d seconds"
                .formatted(limit, window.toSeconds(), retryAfterSeconds);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail);
        body.setProperty(RETRY_AFTER_PROPERTY, retryAfterSeconds);
        return body;
    }
}
