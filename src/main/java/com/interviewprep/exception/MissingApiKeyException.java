package com.interviewprep.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 401 for a request that does not identify its client with an API key. As an {@link ErrorResponseException} it is
 * rendered by {@link GlobalExceptionHandler} without a dedicated handler.
 */
public class MissingApiKeyException extends ErrorResponseException {

    public MissingApiKeyException(String headerName) {
        super(
                HttpStatus.UNAUTHORIZED,
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED, "Missing or blank " + headerName + " header; send your API key in it"),
                null);
        getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"" + headerName + "\"");
    }
}
