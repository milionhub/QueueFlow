package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Token issuing, checked by decoding the result with the production decoder.
 * The clock is fixed at "now" so the token is currently valid while exact
 * iat/exp values remain predictable.
 */
class AccessTokenServiceTest {

    private static final SecretKey KEY = JwtConfig.hs256Key(
            Base64.getEncoder().encodeToString("unit-test-only-hs256-key-material-32+bytes".getBytes()));
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JwtProperties properties = new JwtProperties("unused-here", "queueflow", Duration.ofHours(1));
    private final AccessTokenService service = new AccessTokenService(
            JwtConfig.hs256Encoder(KEY), properties, Clock.fixed(NOW.plusMillis(700), ZoneOffset.UTC));
    private final JwtDecoder decoder = JwtConfig.hs256Decoder(KEY, "queueflow");

    @Test
    void issuesTokenWhoseSubjectIsTheUserId() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = decoder.decode(service.issue(userId).tokenValue());

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("queueflow");
    }

    @Test
    void expiresExactlyOneConfiguredTtlAfterIssuance() {
        IssuedAccessToken token = service.issue(UUID.randomUUID());
        Jwt jwt = decoder.decode(token.tokenValue());

        assertThat(jwt.getIssuedAt()).isEqualTo(NOW); // truncated to whole seconds
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void containsExactlyTheApprovedClaims() {
        Jwt jwt = decoder.decode(service.issue(UUID.randomUUID()).tokenValue());

        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iss", "iat", "exp");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
    }

    @Test
    void neverPrintsTheTokenValue() {
        IssuedAccessToken token = service.issue(UUID.randomUUID());

        assertThat(token.toString()).doesNotContain(token.tokenValue()).contains("<redacted>");
    }

    @Test
    void requiresAUserId() {
        assertThatNullPointerException().isThrownBy(() -> service.issue(null));
    }
}
