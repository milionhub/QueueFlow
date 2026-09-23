package com.queueflow.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.queueflow.security.IssuedAccessToken;
import com.queueflow.user.UserRole;
import com.queueflow.user.dto.UserResponse;

/** Records print every component by default; these carry credentials. */
class AuthDtoRedactionTest {

    private static final String PASSWORD = "plaintext-Pa55word";

    @Test
    void registerRequestNeverPrintsThePassword() {
        String printed = new RegisterRequest("Ada", "ada@example.com", PASSWORD, "Acme").toString();

        assertThat(printed).doesNotContain(PASSWORD).contains("password=<redacted>", "ada@example.com", "Acme");
    }

    @Test
    void loginRequestNeverPrintsThePassword() {
        String printed = new LoginRequest("ada@example.com", PASSWORD).toString();

        assertThat(printed).doesNotContain(PASSWORD).contains("password=<redacted>", "ada@example.com");
    }

    @Test
    void authResponseNeverPrintsTheToken() {
        UserResponse user = new UserResponse(UUID.randomUUID(), "Ada", "ada@example.com", UserRole.ADMIN,
                UUID.randomUUID(), null, null);
        AuthResponse response = AuthResponse.of(new IssuedAccessToken("secret.jwt.value", 3600), user);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.toString()).doesNotContain("secret.jwt.value").contains("accessToken=<redacted>");
    }

    @Test
    void equalityStillUsesEveryComponent() {
        // toString is overridden, equals/hashCode are not.
        assertThat(new LoginRequest("ada@example.com", "one-password"))
                .isNotEqualTo(new LoginRequest("ada@example.com", "another-password"));
    }
}
