package com.queueflow.security;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 signing and verification of QueueFlow access tokens, using Spring
 * Security's Nimbus-backed support. The key is decoded from the configured
 * secret exactly once, here, and shared by the encoder and the decoder; it is
 * not exposed as a bean.
 *
 * The decoder is used by SecurityConfig's oauth2ResourceServer() bearer
 * authentication; the encoder by AccessTokenService, which issues the tokens.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /** RFC 7518 3.2: an HS256 key must be at least as long as the hash output. */
    static final int MIN_KEY_BYTES = 32;

    private final SecretKey signingKey;
    private final String issuer;

    public JwtConfig(JwtProperties properties) {
        this.signingKey = hs256Key(properties.secret());
        this.issuer = properties.issuer();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return hs256Encoder(signingKey);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return hs256Decoder(signingKey, issuer);
    }

    /**
     * Decodes the configured secret: standard Base64 (RFC 4648 section 4, e.g.
     * {@code openssl rand -base64 32}) of at least 32 bytes. Messages never
     * include the secret, and the JDK decoder's exception is deliberately not
     * chained because its message quotes the offending character.
     */
    static SecretKey hs256Key(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "queueflow.security.jwt.secret is not set: provide a Base64-encoded key via JWT_SECRET");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Secret.strip());
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalStateException("queueflow.security.jwt.secret is not valid Base64");
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("queueflow.security.jwt.secret must decode to at least "
                    + MIN_KEY_BYTES + " bytes for HS256, but decodes to " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    static JwtEncoder hs256Encoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Accepts only HS256 (the token's own alg header is never trusted to pick
     * the algorithm). Validation: signature, then issuer, exp and nbf with
     * the framework's default 60s clock skew, plus the framework's default
     * typ check. exp is required: JwtTimestampValidator accepts a token
     * without exp by default, which would make a token valid forever.
     */
    static JwtDecoder hs256Decoder(SecretKey key, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(new JwtIssuerValidator(issuer), timestamps));
        return decoder;
    }
}
