package com.queueflow.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues QueueFlow access tokens. Only issues: validation is the JwtDecoder's.
 *
 * Claims are deliberately minimal - sub (the user's id), iss, iat and exp.
 * Role, workspace, email and name are left out: they can change after the
 * token is issued, so they are read from the database per request instead.
 */
@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this(jwtEncoder, properties, Clock.systemUTC());
    }

    AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = properties.issuer();
        this.ttl = properties.ttl();
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        // Whole seconds: JWT times are NumericDate seconds, so exp - iat is
        // exactly the configured ttl and matches expiresInSeconds.
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(tokenValue, ttl.toSeconds());
    }
}
