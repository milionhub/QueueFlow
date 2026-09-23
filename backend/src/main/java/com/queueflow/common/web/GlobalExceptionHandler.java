package com.queueflow.common.web;

import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ForbiddenOperationException;
import com.queueflow.common.exception.InvalidRelationshipException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Translates service-layer exceptions and the request errors Spring MVC
 * detects into the standard {@link ApiErrorResponse}. Framework messages are
 * never passed through: they name Java types and method signatures, so each
 * handler uses its own fixed, safe message (at most adding a parameter
 * name, which is part of the public API anyway).
 *
 * Business failures are split by meaning, not by where they are thrown:
 * invalid values (BusinessRuleViolationException, 400), resources that
 * cannot be related (InvalidRelationshipException, 400) and actions the
 * acting user may not perform (ForbiddenOperationException, 403).
 *
 * <ul>
 *   <li>DataIntegrityViolationException (database uniqueness races) is not
 *       handled yet - a following 1.8 step.</li>
 *   <li>There is deliberately no catch-all Exception handler, so unexpected
 *       failures are not disguised as intentional API errors.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------------------------------------------------------------
    // Service-layer exceptions
    // ---------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleAlreadyExists(ResourceAlreadyExistsException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    /** "The requested business value/state is invalid." */
    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<ApiErrorResponse> handleBusinessRule(BusinessRuleViolationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    /** "These resources cannot legally be related." */
    @ExceptionHandler(InvalidRelationshipException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidRelationship(InvalidRelationshipException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    /** "The actor is not allowed to perform this operation." */
    @ExceptionHandler(ForbiddenOperationException.class)
    ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenOperationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    // ---------------------------------------------------------------
    // Request validation / binding (400)
    // ---------------------------------------------------------------

    /**
     * {@code @Valid @RequestBody} failures. The request DTOs' constraint
     * messages already name their field ("name must not be blank"), so they
     * are used as-is. With several violations the messages are de-duplicated,
     * sorted and joined with "; " - deterministic regardless of the order in
     * which the validator reports them.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Request validation failed" : message, request);
    }

    /**
     * The JSON body could not be read: missing, syntactically malformed, or
     * a value that cannot be converted (unknown enum constant, text where a
     * UUID is expected, ...). Jackson's own message names Java types and
     * source positions, so it is never exposed.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed or missing request body", request);
    }

    /** A path variable or query parameter that cannot be converted, e.g. "not-a-uuid" for a UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + exception.getName(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Missing required parameter: " + exception.getParameterName(), request);
    }

    // ---------------------------------------------------------------
    // Routing / protocol (404, 405, 415)
    // ---------------------------------------------------------------

    /**
     * No controller mapping matched, so Spring MVC fell through to its
     * static-resource handler, which found nothing. Not a missing entity -
     * that is ResourceNotFoundException's message - but an unknown URL.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNoEndpoint(NoResourceFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "No endpoint matches this request", request);
    }

    /** Keeps Spring's Allow header, which lists the methods the URL does support. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed", exception.getHeaders(), request);
    }

    /** Keeps Spring's Accept header, which lists the supported request content types. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Content-Type; use application/json",
                exception.getHeaders(), request);
    }

    // ---------------------------------------------------------------

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message,
            HttpServletRequest request) {
        return error(status, message, HttpHeaders.EMPTY, request);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpHeaders headers,
            HttpServletRequest request) {
        // getRequestURI(): path only - never the query string or host.
        ApiErrorResponse body = ApiErrorResponse.of(status, message, request.getRequestURI());
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
