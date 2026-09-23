package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 2.2 wiring in the real application context (with the test-only JWT
 * secret from src/test/resources/config/application.yml): the security
 * primitives exist and work together, and adding them changed nothing about
 * who may call the API. The permitAll assertions are expected to flip when
 * Phase 2.4 turns authentication on.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFoundationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void issuedTokensDecodeWithTheApplicationDecoder() {
        UUID userId = UUID.randomUUID();

        assertThat(jwtDecoder.decode(accessTokenService.issue(userId).tokenValue()).getSubject())
                .isEqualTo(userId.toString());
    }

    /** A JwtDecoder bean makes Boot back off its generated in-memory user. */
    @Test
    void noGeneratedInMemoryUserExists() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void apiStillNeedsNoCredentials() throws Exception {
        UUID unknown = UUID.randomUUID();

        // 404 from the controller, not 401/403 from security.
        mockMvc.perform(get("/api/workspaces/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Workspace not found: " + unknown));
    }

    @Test
    void apiIgnoresBearerTokensForNow() throws Exception {
        UUID unknown = UUID.randomUUID();

        // No bearer-token filter is installed yet: neither a garbage token nor
        // a valid one changes the outcome.
        mockMvc.perform(get("/api/workspaces/{id}", unknown)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/workspaces/{id}", unknown)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessTokenService.issue(UUID.randomUUID()).tokenValue()))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
