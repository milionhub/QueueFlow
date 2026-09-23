package com.queueflow.security;

/**
 * A freshly issued access token and its lifetime, for the future
 * AuthResponse ({@code accessToken}, {@code expiresIn}).
 *
 * @param tokenValue       the compact, signed JWT
 * @param expiresInSeconds seconds from issuance until expiry
 */
public record IssuedAccessToken(String tokenValue, long expiresInSeconds) {

    /** Never prints the token: it is a bearer credential. */
    @Override
    public String toString() {
        return "IssuedAccessToken[tokenValue=<redacted>, expiresInSeconds=%d]".formatted(expiresInSeconds);
    }
}
