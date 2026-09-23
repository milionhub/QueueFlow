package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The security primitives in the real application context (with the test-only
 * JWT secret from src/test/resources/config/application.yml) exist and work
 * together. How requests are authenticated with them is covered by
 * JwtAuthenticationIntegrationTest.
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
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
