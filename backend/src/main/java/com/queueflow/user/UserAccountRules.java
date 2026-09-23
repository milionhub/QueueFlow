package com.queueflow.user;

import java.nio.charset.StandardCharsets;

import com.queueflow.common.exception.BusinessRuleViolationException;

/**
 * The account rules shared by every path that creates a user - registration
 * (a workspace's first ADMIN) and ADMIN member creation - so both accept and
 * store exactly the same names, emails and passwords. Each method returns
 * the value to store, or throws BusinessRuleViolationException (400).
 *
 * Checked on the normalized values, and in the service rather than only by
 * bean validation, because services can be called without it and
 * normalizing can change length.
 */
public final class UserAccountRules {

    // Match workspaces.name / users.name VARCHAR(255) and users.email VARCHAR(320).
    public static final int NAME_MAX_LENGTH = 255;
    public static final int EMAIL_MAX_LENGTH = 320;
    public static final int PASSWORD_MIN_CHARACTERS = 8;
    // BCrypt only uses the first 72 bytes of its input, and Spring Security's
    // encoder refuses anything longer; checked here so it is a 400, never a 500.
    public static final int PASSWORD_MAX_UTF8_BYTES = 72;

    private UserAccountRules() {
    }

    /** Trimmed, not blank, at most 255 characters. Also used for workspace names. */
    public static String validatedName(String field, String value) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleViolationException(field + " must not be blank");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BusinessRuleViolationException(field + " must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    /** Normalized with {@link EmailAddresses#normalize}, then checked for length and syntax. */
    public static String validatedEmail(String email) {
        String normalized = email == null ? "" : EmailAddresses.normalize(email);
        if (normalized.isEmpty()) {
            throw new BusinessRuleViolationException("email must not be blank");
        }
        if (normalized.length() > EMAIL_MAX_LENGTH) {
            throw new BusinessRuleViolationException(
                    "email must be at most " + EMAIL_MAX_LENGTH + " characters");
        }
        if (!EmailAddresses.isValid(normalized)) {
            throw new BusinessRuleViolationException("email must be a valid email address");
        }
        return normalized;
    }

    /** Returned unchanged: passwords are never trimmed or otherwise normalized. */
    public static String validatedPassword(String password) {
        if (password == null) {
            throw new BusinessRuleViolationException("password is required");
        }
        if (password.codePointCount(0, password.length()) < PASSWORD_MIN_CHARACTERS) {
            throw new BusinessRuleViolationException(
                    "password must be at least " + PASSWORD_MIN_CHARACTERS + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_UTF8_BYTES) {
            throw new BusinessRuleViolationException(
                    "password must be at most " + PASSWORD_MAX_UTF8_BYTES + " bytes when UTF-8 encoded");
        }
        return password;
    }
}
