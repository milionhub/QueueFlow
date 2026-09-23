package com.queueflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.queueflow.auth.dto.RegisterRequest;

/**
 * Registration and login through the full stack - HTTP, security filter
 * chain, AuthService, JPA and real PostgreSQL (including V5's
 * case-insensitive email index). No @Transactional: every registration must
 * commit or roll back for real, so cleanup is manual. Every email and
 * workspace name carries this run's tag, which the cleanup targets.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String PASSWORD = "integration-Pa55word";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final String tag = "authit-" + UUID.randomUUID().toString().substring(0, 8);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        jdbcTemplate.update("DELETE FROM users WHERE lower(email) LIKE ?", tag + "%");
        jdbcTemplate.update("DELETE FROM workspaces WHERE name LIKE ?", tag + "%");
    }

    private String email(String localPart) {
        return tag + "-" + localPart + "@example.com";
    }

    private MvcResult register(String email, String workspaceName) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "  Ada Lovelace ", "email": "%s", "password": "%s", "workspaceName": "%s"}
                """.formatted(email, PASSWORD, workspaceName))).andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password))).andReturn();
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private int usersWithEmail(String email) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE lower(email) = lower(?)", Integer.class,
                email);
    }

    private int workspacesNamed(String name) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM workspaces WHERE name = ?", Integer.class, name);
    }

    // ------------------------------------------------------------------

    @Test
    void registerPersistsWorkspaceAndAdminWithBcryptHashAndReturnsAValidToken() throws Exception {
        MvcResult result = register("  " + email("Ada").toUpperCase() + " ", "  " + tag + " Acme ");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        UUID userId = UUID.fromString(read(result, "$.user.id"));
        UUID workspaceId = UUID.fromString(read(result, "$.user.workspaceId"));
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/users/" + userId);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD, "password");

        Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT name, email, role, password_hash, workspace_id FROM users WHERE id = ?", userId);
        assertThat(user.get("name")).isEqualTo("Ada Lovelace");
        assertThat(user.get("email")).isEqualTo(email("ada"));
        assertThat(user.get("role")).isEqualTo("ADMIN");
        assertThat(user.get("workspace_id")).isEqualTo(workspaceId);
        assertThat((String) user.get("password_hash")).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM workspaces WHERE id = ?", String.class, workspaceId))
                .isEqualTo(tag + " Acme");

        Jwt jwt = jwtDecoder.decode(read(result, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat((Integer) read(result, "$.expiresIn")).isEqualTo(3600);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void loginSucceedsOnlyWithTheRightPasswordAndFailsIdenticallyOtherwise() throws Exception {
        MvcResult registered = register(email("grace"), tag + " Login");
        String userId = read(registered, "$.user.id");

        MvcResult ok = login("  " + email("GRACE") + " ", PASSWORD);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(jwtDecoder.decode(read(ok, "$.accessToken")).getSubject()).isEqualTo(userId);
        assertThat((String) read(ok, "$.user.id")).isEqualTo(userId);

        MvcResult wrongPassword = login(email("grace"), PASSWORD + "x");
        MvcResult unknownEmail = login(email("nobody"), PASSWORD);
        for (MvcResult failure : List.of(wrongPassword, unknownEmail)) {
            assertThat(failure.getResponse().getStatus()).isEqualTo(401);
            assertThat((String) read(failure, "$.message")).isEqualTo("Invalid email or password");
            assertThat((String) read(failure, "$.error")).isEqualTo("Unauthorized");
        }
        // Same status, same body apart from the timestamp.
        assertThat(wrongPassword.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", ""))
                .isEqualTo(unknownEmail.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", ""));
    }

    @Test
    void duplicateEmailInAnyCaseIs409AndLeavesNoSecondWorkspace() throws Exception {
        assertThat(register(email("dup"), tag + " First").getResponse().getStatus()).isEqualTo(201);

        for (String variant : List.of(email("dup"), email("DUP").toUpperCase(), " " + email("Dup") + " ")) {
            MvcResult duplicate = register(variant, tag + " Second");
            assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
            assertThat((String) read(duplicate, "$.message")).isEqualTo("Email is already registered");
        }
        assertThat(usersWithEmail(email("dup"))).isEqualTo(1);
        assertThat(workspacesNamed(tag + " Second")).isZero();
    }

    /**
     * A failure after the workspace insert must roll it back. Realistic and
     * without any test hook in production code: a legacy row whose email
     * differs only in case (inserted directly, bypassing normalization)
     * passes the service's exact-match pre-check, the workspace is inserted,
     * and then V5's case-insensitive index rejects the user insert.
     */
    @Test
    void failureAfterWorkspaceInsertRollsTheWorkspaceBack() throws Exception {
        UUID legacyWorkspace = jdbcTemplate.queryForObject(
                "INSERT INTO workspaces (name) VALUES (?) RETURNING id", UUID.class, tag + " Legacy");
        jdbcTemplate.update("INSERT INTO users (name, email, password_hash, role, workspace_id) "
                + "VALUES ('Legacy', ?, 'dummy-bcrypt-like-not-a-real-hash', 'MEMBER', ?)",
                email("LEGACY").toUpperCase(), legacyWorkspace);

        MvcResult result = register(email("legacy"), tag + " Orphan");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) read(result, "$.message")).isEqualTo("Resource conflicts with existing data");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("uq_", "duplicate key", "SQL");
        assertThat(workspacesNamed(tag + " Orphan")).as("workspace must be rolled back").isZero();
        assertThat(usersWithEmail(email("legacy"))).isEqualTo(1);
    }

    /** Fixture-style rows with a placeholder hash: the account cannot log in, and it is not a 500. */
    @Test
    void accountWithUnusableStoredHashGetsTheGeneric401() throws Exception {
        UUID workspace = jdbcTemplate.queryForObject(
                "INSERT INTO workspaces (name) VALUES (?) RETURNING id", UUID.class, tag + " Placeholder");
        jdbcTemplate.update("INSERT INTO users (name, email, password_hash, role, workspace_id) "
                + "VALUES ('Placeholder', ?, 'dummy-bcrypt-like-not-a-real-hash', 'MEMBER', ?)",
                email("placeholder"), workspace);

        MvcResult result = login(email("placeholder"), "dummy-bcrypt-like-not-a-real-hash");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) read(result, "$.message")).isEqualTo("Invalid email or password");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("encoder", "prefix", "bcrypt");
    }

    /**
     * The deterministic race (same technique as DatabaseConflictRaceIntegrationTest):
     * registration A for "Race@..." is written and held uncommitted; request B
     * for "race@..." cannot see it, passes the pre-check, inserts its own
     * workspace and blocks on the users unique index; then A commits.
     */
    @Test
    void concurrentCaseVariantRegistrationLeavesExactlyOneUserAndWorkspace() throws Exception {
        CountDownLatch aWritten = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> a = executor.submit(() -> transaction.executeWithoutResult(status -> {
            authService.register(new RegisterRequest("A", email("Race"), PASSWORD, tag + " Race A"));
            aWritten.countDown();
            awaitQuietly(releaseA);
        }));
        assertThat(aWritten.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        Future<MvcResult> b = executor.submit(() -> register(email("race").toUpperCase(), tag + " Race B"));
        awaitSessionBlockedOnLock();
        releaseA.countDown();
        a.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        MvcResult result = b.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) read(result, "$.message")).isEqualTo("Resource conflicts with existing data");
        assertThat(usersWithEmail(email("race"))).isEqualTo(1);
        assertThat(workspacesNamed(tag + " Race A")).isEqualTo(1);
        assertThat(workspacesNamed(tag + " Race B")).as("loser's workspace must be rolled back").isZero();
    }

    /** Two real concurrent requests, no orchestration: whatever the interleaving, one wins. */
    @Test
    void simultaneousRegistrationsOfCaseVariantsProduceOneAccount() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<MvcResult> first = executor.submit(() -> {
            awaitQuietly(start);
            return register(email("Twin"), tag + " Twin 1");
        });
        Future<MvcResult> second = executor.submit(() -> {
            awaitQuietly(start);
            return register(email("TWIN").toUpperCase(), tag + " Twin 2");
        });
        start.countDown();

        List<Integer> statuses = List.of(first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).getResponse().getStatus(),
                second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(usersWithEmail(email("twin"))).isEqualTo(1);
        assertThat(workspacesNamed(tag + " Twin 1") + workspacesNamed(tag + " Twin 2")).isEqualTo(1);
    }

    @Test
    void theIssuedTokenIsWhatOpensTheRestOfTheApi() throws Exception {
        MvcResult registered = register(email("open"), tag + " Open");
        String workspaceId = read(registered, "$.user.workspaceId");
        String token = read(registered, "$.accessToken");

        assertThat(mockMvc.perform(get("/api/workspaces/{id}/members", workspaceId)).andReturn()
                .getResponse().getStatus()).isEqualTo(401);
        MvcResult members = mockMvc.perform(get("/api/workspaces/{id}/members", workspaceId)
                .header("Authorization", "Bearer " + token)).andReturn();

        assertThat(members.getResponse().getStatus()).isEqualTo(200);
        assertThat((List<String>) read(members, "$[*].email")).containsExactly(email("open"));
        assertThat(members.getResponse().getContentAsString()).doesNotContain("password");
    }

    // ------------------------------------------------------------------

    private void awaitSessionBlockedOnLock() throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Integer blocked = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (blocked != null && blocked > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Request B never blocked on registration A's uncommitted row");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertThat(latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
