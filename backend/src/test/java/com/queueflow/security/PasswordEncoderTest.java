package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The production PasswordEncoder bean. BCrypt is salted, so no assertion
 * compares against a fixed hash.
 */
class PasswordEncoderTest {

    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void encodesWithBcryptBehindTheDelegatingPrefix() {
        String encoded = encoder.encode("correct horse battery staple");

        assertThat(encoded).isNotEqualTo("correct horse battery staple");
        assertThat(encoded).startsWith("{bcrypt}$2");
        assertThat(encoded).hasSizeLessThanOrEqualTo(255); // users.password_hash VARCHAR(255)
    }

    @Test
    void matchesOnlyTheOriginalPassword() {
        String encoded = encoder.encode("correct horse battery staple");

        assertThat(encoder.matches("correct horse battery staple", encoded)).isTrue();
        assertThat(encoder.matches("Correct horse battery staple", encoded)).isFalse();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }

    @Test
    void saltsEveryEncoding() {
        assertThat(encoder.encode("same password")).isNotEqualTo(encoder.encode("same password"));
    }
}
