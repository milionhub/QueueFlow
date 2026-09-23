package com.queueflow.user;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The single email normalization rule for user identity: surrounding
 * whitespace removed, then lower-cased with Locale.ROOT (never the JVM's
 * default locale, which would turn "I" into a dotless i under Turkish).
 *
 * Every path that stores or looks up a user's email must use it - today
 * registration and login, later ADMIN member creation - so that
 * "Ada@Example.COM" and "ada@example.com" are one identity. The database
 * backs this up with a case-insensitive unique index (V5).
 */
public final class EmailAddresses {

    /**
     * Deliberately permissive: one "@", something before it, a dotted domain
     * after it, and no whitespace. It catches typos and non-emails; whether
     * an address really receives mail is not decidable by syntax.
     */
    private static final Pattern SYNTAX = Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");

    private EmailAddresses() {
    }

    public static String normalize(String email) {
        Objects.requireNonNull(email, "email must not be null");
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Syntax check for an already-normalized address. */
    public static boolean isValid(String normalizedEmail) {
        return normalizedEmail != null && SYNTAX.matcher(normalizedEmail).matches();
    }
}
