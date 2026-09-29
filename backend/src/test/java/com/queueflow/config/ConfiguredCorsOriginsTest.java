package com.queueflow.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.auth.AuthController;
import com.queueflow.auth.AuthService;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.user.UserService;

/**
 * The allowed origins come from configuration (queueflow.cors.allowed-origins,
 * or QUEUEFLOW_CORS_ALLOWED_ORIGINS): every configured origin is allowed -
 * the development default only when it is listed - and any other is still
 * refused. SecurityConfigCorsTest covers the default policy in depth.
 */
@WebMvcTest(AuthController.class)
@Import(WebSecurityTestConfiguration.class)
@TestPropertySource(properties = "queueflow.cors.allowed-origins=http://localhost:5173, https://queueflow.example")
class ConfiguredCorsOriginsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "https://queueflow.example"})
    void everyConfiguredOriginIsAllowed(String origin) throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:9999", "https://evil.example", "https://queueflow.example.evil"})
    void anyOtherOriginIsStillRefused(String origin) throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
