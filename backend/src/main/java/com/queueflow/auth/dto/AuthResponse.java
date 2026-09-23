package com.queueflow.auth.dto;

import com.queueflow.security.IssuedAccessToken;
import com.queueflow.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The result of registration and of login: an access token for the
 * Authorization header, and the user it identifies. No refresh token - the
 * client logs in again once the token expires.
 */
public record AuthResponse(
        @Schema(description = "Signed JWT access token, sent as \"Authorization: Bearer <accessToken>\"")
        String accessToken,

        @Schema(description = "Always \"Bearer\"", example = "Bearer")
        String tokenType,

        @Schema(description = "Seconds until the access token expires", example = "3600")
        long expiresIn,

        UserResponse user) {

    public static final String BEARER = "Bearer";

    public static AuthResponse of(IssuedAccessToken token, UserResponse user) {
        return new AuthResponse(token.tokenValue(), BEARER, token.expiresInSeconds(), user);
    }

    /** Never prints the token: it is a bearer credential. */
    @Override
    public String toString() {
        return "AuthResponse[accessToken=<redacted>, tokenType=%s, expiresIn=%d, user=%s]"
                .formatted(tokenType, expiresIn, user);
    }
}
