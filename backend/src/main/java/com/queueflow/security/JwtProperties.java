package com.queueflow.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QueueFlow access-token settings, bound from {@code queueflow.security.jwt.*}
 * (see application.yml).
 *
 * The secret is deliberately NOT validated here: a failure during property
 * binding is reported by Boot's bind failure analyzer, which prints the
 * offending property value. The secret is instead decoded and validated once,
 * in {@link JwtConfig}, with messages that never contain it.
 *
 * @param secret Base64-encoded HS256 key material (supplied via JWT_SECRET)
 * @param issuer value of the {@code iss} claim, issued and required
 * @param ttl    access-token lifetime
 */
@ConfigurationProperties("queueflow.security.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("queueflow.security.jwt.issuer must not be blank");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("queueflow.security.jwt.ttl must be a positive duration");
        }
    }

    /** Never prints the secret: records otherwise include every component. */
    @Override
    public String toString() {
        return "JwtProperties[secret=<redacted>, issuer=%s, ttl=%s]".formatted(issuer, ttl);
    }
}
