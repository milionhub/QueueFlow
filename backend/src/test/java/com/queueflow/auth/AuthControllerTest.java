package com.queueflow.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.queueflow.auth.dto.AuthResponse;
import com.queueflow.auth.dto.LoginRequest;
import com.queueflow.auth.dto.RegisterRequest;
import com.queueflow.common.exception.InvalidCredentialsException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.config.SecurityConfig;
import com.queueflow.user.UserRole;
import com.queueflow.user.dto.UserResponse;

/**
 * Web slice for the auth endpoints: routing, JSON shape, bean validation,
 * status codes and error bodies through the real SecurityConfig (both
 * endpoints are reachable without credentials), with AuthService mocked.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String PASSWORD = "s3cret-Pa55word";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private static AuthResponse authResponse(UUID userId, UUID workspaceId) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30.123456Z");
        return new AuthResponse("header.payload.signature", "Bearer", 3600,
                new UserResponse(userId, "Ada", "ada@example.com", UserRole.ADMIN, workspaceId, timestamp, timestamp));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String registerBody(String name, String email, String password, String workspaceName) {
        return """
                {"name": %s, "email": %s, "password": %s, "workspaceName": %s}
                """.formatted(quoted(name), quoted(email), quoted(password), quoted(workspaceName));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void registerReturns201WithLocationAndExactlyTheAuthResponseFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(authService.register(any())).thenReturn(authResponse(userId, workspaceId));

        mockMvc.perform(json(post("/api/auth/register"),
                        registerBody("Ada", "Ada@Example.com", PASSWORD, "Acme Inc.")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/users/" + userId))
                .andExpect(jsonPath("$.accessToken").value("header.payload.signature"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.user.length()").value(7))
                .andExpect(content().string(not(containsString("password"))));

        verify(authService).register(new RegisterRequest("Ada", "Ada@Example.com", PASSWORD, "Acme Inc."));
    }

    /** Registration has no role or workspaceId input: extra JSON properties are simply ignored. */
    @Test
    void registerIgnoresClientSuppliedRoleAndWorkspace() throws Exception {
        when(authService.register(any())).thenReturn(authResponse(UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(json(post("/api/auth/register"), """
                        {"name": "Ada", "email": "ada@example.com", "password": "%s", "workspaceName": "Acme",
                         "role": "MEMBER", "workspaceId": "%s"}
                        """.formatted(PASSWORD, UUID.randomUUID())))
                .andExpect(status().isCreated());

        verify(authService).register(new RegisterRequest("Ada", "ada@example.com", PASSWORD, "Acme"));
    }

    @Test
    void registerValidationFailuresAre400AndNeverEchoThePassword() throws Exception {
        String shortPassword = "short77";

        mockMvc.perform(json(post("/api/auth/register"), registerBody(" ", "not-an-email", shortPassword, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name must not be blank; "
                        + "password must be at least 8 characters; workspaceName must not be blank"))
                .andExpect(content().string(not(containsString(shortPassword))));

        mockMvc.perform(json(post("/api/auth/register"), registerBody("Ada", "ada@example.com", null, "Acme")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password is required"));

        verify(authService, never()).register(any());
    }

    /** Length and email syntax are the service's, on normalized values: a padded email is fine here. */
    @Test
    void registerLeavesNormalizationDependentRulesToTheService() throws Exception {
        when(authService.register(any())).thenReturn(authResponse(UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(json(post("/api/auth/register"),
                        registerBody("  Ada  ", "  Ada@Example.COM  ", PASSWORD, "  Acme  ")))
                .andExpect(status().isCreated());

        verify(authService).register(new RegisterRequest("  Ada  ", "  Ada@Example.COM  ", PASSWORD, "  Acme  "));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(authService.register(any())).thenThrow(new ResourceAlreadyExistsException("Email is already registered"));

        mockMvc.perform(json(post("/api/auth/register"), registerBody("Ada", "ada@example.com", PASSWORD, "Acme")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void loginReturns200WithAuthResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.login(any())).thenReturn(authResponse(userId, UUID.randomUUID()));

        mockMvc.perform(json(post("/api/auth/login"), """
                        {"email": " Ada@Example.com ", "password": "%s"}
                        """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.id").value(userId.toString()));

        verify(authService).login(new LoginRequest(" Ada@Example.com ", PASSWORD));
    }

    @Test
    void invalidCredentialsAre401WithTheGenericMessage() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(json(post("/api/auth/login"), """
                        {"email": "ada@example.com", "password": "%s"}
                        """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    void loginRequiresEmailAndPassword() throws Exception {
        mockMvc.perform(json(post("/api/auth/login"), """
                        {"email": " ", "password": ""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email must not be blank; password is required"));

        verify(authService, never()).login(any());
    }

    @Test
    void thereIsNoMeEndpointYet() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isNotFound());
    }
}
