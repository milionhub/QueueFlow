package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;

/**
 * Bearer authentication end to end with real tokens: Spring Security's
 * BearerTokenAuthenticationFilter, the application's HS256 decoder, the
 * current-user converter against real PostgreSQL, and the JSON 401/403
 * contract. No @Transactional; everything this test creates carries its tag
 * and is deleted afterwards.
 *
 * PrincipalEchoController (test-only, below) returns the resolved
 * AuthenticatedUser, so the principal itself can be checked over HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtAuthenticationIntegrationTest.PrincipalEcho.class)
class JwtAuthenticationIntegrationTest {

    private static final String PASSWORD = "jwt-it-Pa55word";

    @TestConfiguration
    @Import(PrincipalEchoController.class)
    static class PrincipalEcho {
    }

    @RestController
    static class PrincipalEchoController {

        @GetMapping("/api/test-support/principal")
        AuthenticatedUser principal(@AuthenticationPrincipal AuthenticatedUser user) {
            return user;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String tag = "jwtit-" + UUID.randomUUID().toString().substring(0, 8);

    private String userId;
    private String workspaceId;
    private String registrationToken;

    @BeforeEach
    void registerAdmin() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Ada", "email": "%s@example.com", "password": "%s", "workspaceName": "%s"}
                        """.formatted(tag, PASSWORD, tag))).andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        userId = read(registered, "$.user.id");
        workspaceId = read(registered, "$.user.workspaceId");
        registrationToken = read(registered, "$.accessToken");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM projects WHERE workspace_id IN (SELECT id FROM workspaces WHERE name = ?)", tag);
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", tag + "%");
        jdbcTemplate.update("DELETE FROM workspaces WHERE name = ?", tag);
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn();
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** A token signed with the application's own key, with claims as the customizer leaves them. */
    private String token(Consumer<JwtClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(userId).issuer("queueflow").issuedAt(now).expiresAt(now.plus(Duration.ofHours(1)));
        customizer.accept(claims);
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims.build())).getTokenValue();
    }

    // ------------------------------------------------------------------
    // valid tokens
    // ------------------------------------------------------------------

    @Test
    void registrationAndLoginTokensBothAuthenticateAsThatUser() throws Exception {
        MvcResult login = perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s@example.com", "password": "%s"}
                """.formatted(tag, PASSWORD)));
        String loginToken = read(login, "$.accessToken");

        for (String token : List.of(registrationToken, loginToken)) {
            MvcResult me = perform(withToken(get("/api/auth/me"), token));
            assertThat(me.getResponse().getStatus()).isEqualTo(200);
            assertThat((String) read(me, "$.id")).isEqualTo(userId);
            assertThat((String) read(me, "$.email")).isEqualTo(tag + "@example.com");
            assertThat(me.getResponse().getContentAsString()).doesNotContain("password");

            MvcResult members = perform(withToken(get("/api/workspaces/{id}/members", workspaceId), token));
            assertThat(members.getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void principalCarriesTheCurrentDatabaseRoleAndWorkspace() throws Exception {
        MvcResult principal = perform(withToken(get("/api/test-support/principal"), registrationToken));
        assertThat((String) read(principal, "$.userId")).isEqualTo(userId);
        assertThat((String) read(principal, "$.workspaceId")).isEqualTo(workspaceId);
        assertThat((String) read(principal, "$.role")).isEqualTo("ADMIN");

        // A role change applies to the very next request with the same token.
        jdbcTemplate.update("UPDATE users SET role = 'MEMBER' WHERE id = ?::uuid", userId);

        MvcResult demoted = perform(withToken(get("/api/test-support/principal"), registrationToken));
        assertThat((String) read(demoted, "$.role")).isEqualTo("MEMBER");
        assertThat((String) read(perform(withToken(get("/api/auth/me"), registrationToken)), "$.role"))
                .isEqualTo("MEMBER");
    }

    @Test
    void roleAndWorkspaceClaimsInATokenAreIgnored() throws Exception {
        String misleading = token(claims -> claims
                .claim("role", "MEMBER")
                .claim("workspaceId", UUID.randomUUID().toString())
                .claim("scope", "admin"));

        MvcResult principal = perform(withToken(get("/api/test-support/principal"), misleading));

        assertThat(principal.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) read(principal, "$.workspaceId")).isEqualTo(workspaceId);
        assertThat((String) read(principal, "$.role")).isEqualTo("ADMIN");
    }

    @Test
    void authenticatedRequestsCreateNoSessionOrCookie() throws Exception {
        MvcResult result = perform(withToken(get("/api/auth/me"), registrationToken));

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    // ------------------------------------------------------------------
    // rejected requests: the JSON 401 contract
    // ------------------------------------------------------------------

    @Test
    void missingTokenIs401AuthenticationRequired() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(get("/api/auth/me"),
                get("/api/workspaces/{id}", workspaceId),
                withBasicAuthOnly(get("/api/auth/me")))) {
            MvcResult result = perform(request);

            assertUnauthorized(result, "Authentication required", "Bearer");
        }
    }

    @Test
    void everyKindOfBadTokenIsTheSame401() throws Exception {
        String deletedUsersToken = deletedUsersToken();
        List<String> badTokens = List.of(
                "not-a-jwt",
                "a.b.c",
                signedWithAnotherKey(),
                token(claims -> claims.issuedAt(Instant.now().minus(Duration.ofHours(3)))
                        .expiresAt(Instant.now().minus(Duration.ofHours(2)))),
                token(claims -> claims.issuer("someone-else")),
                token(claims -> claims.claims(map -> map.remove("exp"))),
                token(claims -> claims.claims(map -> map.remove("sub"))),
                token(claims -> claims.subject("not-a-uuid")),
                token(claims -> claims.subject(UUID.randomUUID().toString())),
                deletedUsersToken);

        for (String badToken : badTokens) {
            MvcResult result = perform(withToken(get("/api/auth/me"), badToken));

            assertUnauthorized(result, "Invalid or expired token", "Bearer error=\"invalid_token\"");
        }
    }

    @Test
    void rejectedWriteHasNoSideEffect() throws Exception {
        String body = """
                {"name": "Should not exist", "key": "NOPE"}
                """;

        assertUnauthorized(perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body)),
                "Authentication required", "Bearer");
        assertUnauthorized(perform(withToken(post("/api/projects"), signedWithAnotherKey())
                .contentType(MediaType.APPLICATION_JSON).content(body)), "Invalid or expired token",
                "Bearer error=\"invalid_token\"");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM projects WHERE workspace_id = ?::uuid",
                Integer.class, workspaceId)).isZero();

        // The same request with a valid token goes through.
        MvcResult created = perform(withToken(post("/api/projects"), registrationToken)
                .contentType(MediaType.APPLICATION_JSON).content(body));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
    }

    /** Security-layer 403: an authenticated caller on a path the rules do not serve at all. */
    @Test
    void authenticatedRequestOutsideTheApiIs403Json() throws Exception {
        MvcResult result = perform(withToken(get("/internal/anything"), registrationToken));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat((Integer) read(result, "$.status")).isEqualTo(403);
        assertThat((String) read(result, "$.error")).isEqualTo("Forbidden");
        assertThat((String) read(result, "$.message")).isEqualTo("Access denied");
        assertThat((String) read(result, "$.path")).isEqualTo("/internal/anything");
        assertThat((String) read(result, "$.timestamp")).isNotBlank();
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        assertThat(perform(get("/actuator/health")).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(get("/v3/api-docs")).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(get("/swagger-ui/index.html")).getResponse().getStatus()).isEqualTo(200);
        // Login with a stale token still works: public endpoints ignore it.
        MvcResult login = perform(withToken(post("/api/auth/login"), signedWithAnotherKey())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s@example.com", "password": "%s"}
                        """.formatted(tag, PASSWORD)));
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------

    private static void assertUnauthorized(MvcResult result, String message, String wwwAuthenticate)
            throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(wwwAuthenticate);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat((Integer) read(result, "$.status")).isEqualTo(401);
        assertThat((String) read(result, "$.error")).isEqualTo("Unauthorized");
        assertThat((String) read(result, "$.message")).isEqualTo(message);
        assertThat((String) read(result, "$.path")).isEqualTo(result.getRequest().getRequestURI());
        assertThat((String) read(result, "$.timestamp")).isNotBlank();
        assertThat(body).doesNotContainIgnoringCase("jwt").doesNotContain("signature", "Exception", "claim");
    }

    private static MockHttpServletRequestBuilder withBasicAuthOnly(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Basic YWRhOnBhc3N3b3Jk");
    }

    private String signedWithAnotherKey() {
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        JwtEncoder foreign = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(otherKey, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build();
        Instant now = Instant.now();
        return foreign.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                JwtClaimsSet.builder().subject(userId).issuer("queueflow").issuedAt(now)
                        .expiresAt(now.plus(Duration.ofHours(1))).build())).getTokenValue();
    }

    /** A perfectly valid token whose user has since been deleted. */
    private String deletedUsersToken() throws Exception {
        MvcResult other = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Gone", "email": "%s-gone@example.com", "password": "%s", "workspaceName": "%s"}
                        """.formatted(tag, PASSWORD, tag))).andReturn();
        String token = read(other, "$.accessToken");
        assertThat(perform(withToken(get("/api/auth/me"), token)).getResponse().getStatus()).isEqualTo(200);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?::uuid", (String) read(other, "$.user.id"));
        return token;
    }
}
