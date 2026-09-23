package com.queueflow.common.web;

import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Translates the service layer's HTTP-agnostic exceptions into the standard
 * {@link ApiErrorResponse}. Phase 1.8A foundation only:
 *
 * <ul>
 *   <li>BusinessRuleViolationException is deliberately NOT handled yet: it
 *       currently covers invalid input, permission failures and
 *       cross-workspace references, which need different statuses. It will
 *       be split before being mapped.</li>
 *   <li>DataIntegrityViolationException (database uniqueness races) is not
 *       handled yet either - a following 1.8 step.</li>
 *   <li>There is deliberately no catch-all Exception handler, so unexpected
 *       failures are not disguised as intentional API errors.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleAlreadyExists(ResourceAlreadyExistsException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

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

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message,
            HttpServletRequest request) {
        // getRequestURI(): path only - never the query string or host.
        ApiErrorResponse body = ApiErrorResponse.of(status, message, request.getRequestURI());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
