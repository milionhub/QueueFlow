package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * What the production decoder accepts and rejects. Tokens are minted with
 * Spring's own encoder; only alg:none has to be written by hand, because no
 * encoder will produce an unsigned token.
 */
class JwtDecoderTest {

    private static final SecretKey KEY = key("unit-test-only-hs256-key-material-48+bytes-long!!");
    private static final SecretKey OTHER_KEY = key("a-different-key-that-queueflow-never-trusted-48b!!");

    private final JwtDecoder decoder = JwtConfig.hs256Decoder(KEY, "queueflow");

    @Test
    void acceptsAValidQueueFlowToken() {
        String subject = UUID.randomUUID().toString();

        assertThat(decoder.decode(token(KEY, MacAlgorithm.HS256, claims -> claims.subject(subject))).getSubject())
                .isEqualTo(subject);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        assertRejected(token(OTHER_KEY, MacAlgorithm.HS256, claims -> { }));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        assertThatExceptionOfType(JwtValidationException.class)
                .isThrownBy(() -> decoder.decode(token(KEY, MacAlgorithm.HS256, claims -> claims.issuer("other"))))
                .withMessageContaining("iss");
    }

    @Test
    void rejectsExpiredToken() {
        // Beyond the 60s clock skew.
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        String expired = token(KEY, MacAlgorithm.HS256,
                claims -> claims.issuedAt(issuedAt).expiresAt(issuedAt.plus(Duration.ofHours(1))));

        assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(expired))
                .withMessageContaining("expired");
    }

    @Test
    void rejectsTokenWithoutExpiry() {
        String neverExpires = token(KEY, MacAlgorithm.HS256, claims -> claims.claims(map -> map.remove("exp")));

        assertRejected(neverExpires);
    }

    @Test
    void rejectsTokenNotYetValid() {
        assertRejected(token(KEY, MacAlgorithm.HS256,
                claims -> claims.notBefore(Instant.now().plus(Duration.ofMinutes(10)))));
    }

    @Test
    void rejectsMalformedTokens() {
        assertRejected("not-a-jwt");
        assertRejected("a.b.c");
        String valid = token(KEY, MacAlgorithm.HS256, claims -> { });
        assertRejected(valid.substring(0, valid.length() - 2)); // truncated signature
    }

    @Test
    void isPinnedToHs256() {
        // Same secret, different HMAC algorithm: the token's own alg header
        // must not be able to choose how it is verified.
        assertRejected(token(KEY, MacAlgorithm.HS384, claims -> { }));
    }

    @Test
    void rejectsUnsignedAlgNoneToken() {
        long now = Instant.now().getEpochSecond();
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + UUID.randomUUID() + "\",\"iss\":\"queueflow\",\"iat\":" + now
                + ",\"exp\":" + (now + 3600) + "}");

        assertRejected(header + "." + payload + ".");
    }

    private void assertRejected(String token) {
        assertThatExceptionOfType(BadJwtException.class).isThrownBy(() -> decoder.decode(token));
    }

    /** A token that is valid unless the customizer breaks it. */
    private static String token(SecretKey key, MacAlgorithm algorithm, Consumer<JwtClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("queueflow")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)));
        customizer.accept(claims);
        JwsHeader header = JwsHeader.with(algorithm).type("JWT").build();
        return NimbusJwtEncoder.withSecretKey(key).algorithm(algorithm).build()
                .encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static SecretKey key(String material) {
        return JwtConfig.hs256Key(Base64.getEncoder().encodeToString(material.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
