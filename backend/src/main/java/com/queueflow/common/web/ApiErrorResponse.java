package com.queueflow.common.web;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The single JSON error body returned by the REST API for errors handled by
 * {@link GlobalExceptionHandler}. Deliberately small for V1: no trace ids,
 * error codes or per-field details, and never a stack trace, exception class
 * name or other internal detail.
 *
 * @param timestamp when the error was produced - UTC, microsecond precision,
 *                  the same representation as every other API timestamp
 * @param status    numeric HTTP status, e.g. 404
 * @param error     the standard HTTP reason phrase, e.g. "Not Found"
 * @param message   a safe, human-readable explanation
 * @param path      the request URI path only (no scheme, host or query string)
 */
public record ApiErrorResponse(
        @Schema(description = "When the error was produced (UTC)")
        OffsetDateTime timestamp,
        @Schema(description = "HTTP status code", example = "404")
        int status,
        @Schema(description = "HTTP reason phrase", example = "Not Found")
        String error,
        @Schema(description = "Human-readable explanation")
        String message,
        @Schema(description = "Request path, without query string", example = "/api/tickets/{ticketId}")
        String path) {

    public static ApiErrorResponse of(HttpStatus status, String message, String path) {
        return new ApiErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS),
                status.value(),
                status.getReasonPhrase(),
                message,
                path);
    }
}
