package com.queueflow.security;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.queueflow.config.SecurityConfig;
import com.queueflow.user.UserRepository;

/**
 * The production security setup for @WebMvcTest slices, which do not load
 * @Configuration or @Component classes on their own: the real filter chain
 * and rules (SecurityConfig), the real HS256 decoder (JwtConfig, with the
 * test-only secret) and the real JSON 401/403 handler.
 *
 * The one stand-in is UserRepository, which slices have no JPA for: the
 * security chain needs it to resolve a bearer token's user. Slice tests
 * authenticate with @WithAuthenticatedUser instead of tokens, so it is
 * never called there.
 */
@TestConfiguration
@Import({SecurityConfig.class, JwtConfig.class, ApiSecurityErrorHandler.class})
public class WebSecurityTestConfiguration {

    @Bean
    UserRepository userRepository() {
        return mock(UserRepository.class);
    }
}
