package com.queueflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing for user credentials. The delegating encoder currently
 * hashes with BCrypt and stores the algorithm as a prefix ({bcrypt}...), so
 * the default can be upgraded later without invalidating existing hashes.
 */
@Configuration
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
