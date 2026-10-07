package com.interviewprep.exception;

import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Translates every exception raised while handling an API request into an RFC 9457 Problem Details body.
 *
 * <p>Spring MVC exceptions (405, 415, 404 for unknown paths, ...) keep the status chosen by
 * {@link ResponseEntityExceptionHandler}; validation and conversion failures add a field-level {@code errors} array.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI ABOUT_BLANK = URI.create("about:blank");
    private static final String ERRORS_PROPERTY = "errors";
    private static final String VALIDATION_FAILED = "Validation failed";

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceGoneException.class)
    ResponseEntity<Object> handleGone(ResourceGoneException ex, WebRequest request) {
        return problem(ex, HttpStatus.GONE, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<Object> handleConflict(ResourceConflictException ex, WebRequest request) {
        return problem(ex, HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<Object> handleBusinessRule(BusinessRuleViolationException ex, WebRequest request) {
        return problem(ex, HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), request);
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<Object> handleFieldValidation(FieldValidationException ex, WebRequest request) {
        FieldErrorResponse error = new FieldErrorResponse(ex.getField(), ex.getMessage());
        return validationProblem(ex, List.of(error), new HttpHeaders(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, WebRequest request) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());
        return problem(
                ex, HttpStatus.CONFLICT, "The resource was modified concurrently; reload it and try again", request);
    }

    // --- File uploads: 413 too large, 415 disallowed type, 400 missing part / malformed multipart ---

    @ExceptionHandler(FileTooLargeException.class)
    ResponseEntity<Object> handleFileTooLarge(FileTooLargeException ex, WebRequest request) {
        return problem(ex, HttpStatus.CONTENT_TOO_LARGE, ex.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    ResponseEntity<Object> handleUnsupportedFileType(UnsupportedFileTypeException ex, WebRequest request) {
        return problem(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request);
    }

    /** Rejected by the multipart parser ({@code spring.servlet.multipart.max-file-size}/{@code max-request-size}). */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ex, HttpStatus.CONTENT_TOO_LARGE, "Uploaded file exceeds the maximum allowed size", request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        FieldErrorResponse error = new FieldErrorResponse(ex.getRequestPartName(), "is required");
        return validationProblem(ex, List.of(error), headers, request);
    }

    /** Any other multipart failure, e.g. a truncated body or a non-multipart request to a multipart endpoint. */
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Object> handleMultipart(MultipartException ex, WebRequest request) {
        return problem(ex, HttpStatus.BAD_REQUEST, "Request is not a valid multipart upload", request);
    }

    // --- End file uploads ---

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error while handling request", ex);
        return problem(ex, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldErrorResponse(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        message(error)))
                .toList();
        return validationProblem(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        // A @Valid body validated together with constrained parameters reports FieldErrors.
                        .map(error -> new FieldErrorResponse(
                                error instanceof FieldError fieldError
                                        ? fieldError.getField()
                                        : result.getMethodParameter().getParameterName(),
                                message(error))))
                .toList();
        return validationProblem(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        FieldErrorResponse error = new FieldErrorResponse(field, invalidValueMessage(ex.getRequiredType()));
        return validationProblem(ex, List.of(error), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            FieldErrorResponse error =
                    new FieldErrorResponse(fieldPath(mismatch), invalidValueMessage(mismatch.getTargetType()));
            return validationProblem(ex, List.of(error), headers, request);
        }
        return problem(ex, HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", request);
    }

    /** Every response produced by this handler passes through here, so all bodies share the documented shape. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problemDetail && problemDetail.getType() == null) {
            problemDetail.setType(ABOUT_BLANK);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatus status, String detail, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }

    private ResponseEntity<Object> validationProblem(
            Exception ex, List<FieldErrorResponse> errors, HttpHeaders headers, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        body.setProperty(ERRORS_PROPERTY, errors);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static String fieldPath(JacksonException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String invalidValueMessage(Class<?> targetType) {
        if (targetType == null) {
            return "has an invalid value";
        }
        if (targetType.isEnum()) {
            String allowed = Arrays.stream(targetType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "must be one of: " + allowed;
        }
        if (LocalDate.class.equals(targetType)) {
            return "must be a valid date in the format yyyy-MM-dd";
        }
        return "must be a valid " + targetType.getSimpleName();
    }

    private static String message(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
    }
}
