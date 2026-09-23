package com.queueflow.common.exception;

/**
 * Thrown by login when the email/password pair does not identify a user.
 * Deliberately one exception with one fixed message for every cause
 * (unknown email, wrong password, unusable stored hash), so the response
 * never reveals which one it was.
 *
 * This is a login-request failure handled like any other service exception.
 * It is unrelated to missing or invalid bearer tokens on other requests,
 * which Spring Security rejects before any controller runs.
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "Invalid email or password";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
