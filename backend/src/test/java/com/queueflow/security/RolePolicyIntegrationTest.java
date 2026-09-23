package com.queueflow.security;

import static com.queueflow.security.TestActors.actorOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserService;
import com.queueflow.user.dto.CreateMemberRequest;

/**
 * The V1 role policy over the real stack: HTTP, real access tokens, the
 * security chain, services, JPA and PostgreSQL.
 *
 * Two registered workspaces: A (Ana, ADMIN, who creates Pedro as a MEMBER
 * through the API; Pedro then logs in with his own password) and B (Bruno,
 * ADMIN). Only creating/updating projects and creating members are
 * ADMIN-only; another workspace's resource is 404 whatever the role; a
 * MEMBER keeps every normal collaboration operation. No @Transactional;
 * both workspaces are deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RolePolicyIntegrationTest {

    private static final String PASSWORD = "role-Pa55word";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final String tag = "role-" + UUID.randomUUID().toString().substring(0, 8);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private record Tenant(UUID workspaceId, UUID adminId, String adminEmail, String token, UUID projectId,
            UUID ticketId, UUID commentId) {
    }

    private Tenant a;
    private Tenant b;
    private UUID pedroId;
    private String pedroEmail;
    private String pedroToken;

    @BeforeEach
    void createTwoWorkspacesAndAMember() throws Exception {
        a = tenant("ana");
        b = tenant("bruno");

        pedroEmail = email("pedro");
        MvcResult pedro = send(a.token(), post("/api/workspaces/{w}/members", a.workspaceId()), """
                {"name": "Pedro", "email": "%s", "password": "%s"}
                """.formatted(pedroEmail, PASSWORD));
        assertThat(status(pedro)).as(body(pedro)).isEqualTo(201);
        pedroId = id(pedro);
        pedroToken = login(pedroEmail, PASSWORD);
    }

    private Tenant tenant(String name) throws Exception {
        String email = email(name);
        MvcResult registered = ok(null, post("/api/auth/register"), """
                {"name": "%s", "email": "%s", "password": "%s", "workspaceName": "%s %s"}
                """.formatted(name, email, PASSWORD, tag, name));
        String token = read(registered, "$.accessToken");
        UUID project = id(ok(token, post("/api/projects"), """
                {"name": "Shop", "key": "ECOM"}
                """));
        UUID ticket = id(ok(token, post("/api/tickets"), """
                {"projectId": "%s", "title": "%s ticket", "status": "TODO", "priority": "LOW"}
                """.formatted(project, name)));
        UUID comment = id(ok(token, post("/api/comments"), """
                {"ticketId": "%s", "content": "%s was here"}
                """.formatted(ticket, name)));
        return new Tenant(UUID.fromString(read(registered, "$.user.workspaceId")),
                UUID.fromString(read(registered, "$.user.id")), email, token, project, ticket, comment);
    }

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        for (Tenant tenant : new Tenant[] {a, b}) {
            if (tenant == null) {
                continue;
            }
            UUID w = tenant.workspaceId();
            String tickets = "SELECT t.id FROM tickets t JOIN projects p ON p.id = t.project_id "
                    + "WHERE p.workspace_id = ?";
            jdbcTemplate.update("DELETE FROM activities WHERE ticket_id IN (" + tickets + ")", w);
            jdbcTemplate.update("DELETE FROM comments WHERE ticket_id IN (" + tickets + ")", w);
            jdbcTemplate.update("DELETE FROM ticket_labels WHERE ticket_id IN (" + tickets + ")", w);
            jdbcTemplate.update("DELETE FROM tickets WHERE id IN (" + tickets + ")", w);
            jdbcTemplate.update("DELETE FROM labels WHERE workspace_id = ?", w);
            jdbcTemplate.update("DELETE FROM projects WHERE workspace_id = ?", w);
            jdbcTemplate.update("DELETE FROM users WHERE workspace_id = ?", w);
            jdbcTemplate.update("DELETE FROM workspaces WHERE id = ?", w);
        }
    }

    // ------------------------------------------------------------------
    // projects: create and update are ADMIN-only
    // ------------------------------------------------------------------

    @Test
    void onlyAnAdminCreatesProjectsAndAMembersAttemptLeavesNoRow() throws Exception {
        MvcResult created = send(a.token(), post("/api/projects"), """
                {"name": "Operations", "key": "OPS"}
                """);
        assertThat(status(created)).isEqualTo(201);
        assertThat((String) read(created, "$.workspaceId")).isEqualTo(a.workspaceId().toString());

        MvcResult refused = send(pedroToken, post("/api/projects"), """
                {"name": "Pedro's project", "key": "PEDRO"}
                """);
        assertForbidden(refused, "Only workspace admins can create projects", "/api/projects");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM projects WHERE workspace_id = ?",
                Integer.class, a.workspaceId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM projects WHERE key = 'PEDRO'",
                Integer.class)).isZero();
    }

    @Test
    void onlyAnAdminUpdatesProjectsWhileForeignAndAbsentProjectsAreNotFoundForEveryRole() throws Exception {
        assertStatus(200, a.token(), patch("/api/projects/{p}", a.projectId()), """
                {"name": "Shop v2"}
                """);

        MvcResult refused = send(pedroToken, patch("/api/projects/{p}", a.projectId()), """
                {"name": "Pedro's shop", "description": null}
                """);
        assertForbidden(refused, "Only workspace admins can update projects", "/api/projects/" + a.projectId());
        assertThat(jdbcTemplate.queryForMap("SELECT name, description FROM projects WHERE id = ?", a.projectId()))
                .containsEntry("name", "Shop v2");

        // Another workspace's project: 404 for the ADMIN and for the MEMBER,
        // exactly like a project that does not exist - never a 403.
        UUID absent = UUID.randomUUID();
        for (String token : List.of(a.token(), pedroToken)) {
            MvcResult foreign = send(token, patch("/api/projects/{p}", b.projectId()), "{\"name\": \"Hijacked\"}");
            MvcResult missing = send(token, patch("/api/projects/{p}", absent), "{\"name\": \"Hijacked\"}");
            assertThat(status(foreign)).isEqualTo(404);
            assertThat(status(missing)).isEqualTo(404);
            assertThat(message(foreign).replace(b.projectId().toString(), "<id>"))
                    .isEqualTo(message(missing).replace(absent.toString(), "<id>"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM projects WHERE id = ?", String.class,
                b.projectId())).isEqualTo("Shop");
    }

    // ------------------------------------------------------------------
    // member creation
    // ------------------------------------------------------------------

    @Test
    void anAdminCreatesAMemberOfTheirOwnWorkspaceWithABcryptPassword() throws Exception {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, email, role, workspace_id, password_hash FROM users WHERE id = ?", pedroId);
        assertThat(row).containsEntry("name", "Pedro").containsEntry("email", pedroEmail)
                .containsEntry("role", "MEMBER").containsEntry("workspace_id", a.workspaceId());
        String hash = (String) row.get("password_hash");
        assertThat(hash).startsWith("{bcrypt}$2");
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();

        MvcResult listed = ok(a.token(), get("/api/workspaces/{w}/members", a.workspaceId()), null);
        assertThat((List<String>) read(listed, "$[*].name")).containsExactly("ana", "Pedro");
        assertThat(body(listed)).doesNotContain(hash, PASSWORD, "password");
    }

    @Test
    void membersCannotCreateMembersAndForeignOrAbsentWorkspacesAreNotFoundForEveryRole() throws Exception {
        int users = userCount();
        String newMember = """
                {"name": "Nobody", "email": "%s", "password": "%s"}
                """.formatted(email("nobody"), PASSWORD);

        assertForbidden(send(pedroToken, post("/api/workspaces/{w}/members", a.workspaceId()), newMember),
                "Only workspace admins can create members", "/api/workspaces/" + a.workspaceId() + "/members");

        UUID absent = UUID.randomUUID();
        for (String token : List.of(a.token(), pedroToken)) {
            MvcResult foreign = send(token, post("/api/workspaces/{w}/members", b.workspaceId()), newMember);
            MvcResult missing = send(token, post("/api/workspaces/{w}/members", absent), newMember);
            assertThat(status(foreign)).isEqualTo(404);
            assertThat(status(missing)).isEqualTo(404);
            assertThat(message(foreign).replace(b.workspaceId().toString(), "<id>"))
                    .isEqualTo(message(missing).replace(absent.toString(), "<id>"));
        }
        assertThat(userCount()).isEqualTo(users);
    }

    /** No request field - in any letter case - chooses the new user's role or workspace. */
    @Test
    void theRequestCannotChooseTheNewMembersRoleOrWorkspace() throws Exception {
        String email = email("escalate");
        MvcResult created = send(a.token(), post("/api/workspaces/{w}/members", a.workspaceId()), """
                {"name": "Escalate", "email": "%s", "password": "%s",
                 "role": "ADMIN", "Role": "ADMIN", "ROLE": "ADMIN", "admin": true, "isAdmin": true,
                 "workspaceId": "%s", "workspace_id": "%s", "userId": "%s", "id": "%s"}
                """.formatted(email, PASSWORD, b.workspaceId(), b.workspaceId(), a.adminId(), a.adminId()));

        assertThat(status(created)).as(body(created)).isEqualTo(201);
        assertThat((String) read(created, "$.role")).isEqualTo("MEMBER");
        assertThat((String) read(created, "$.workspaceId")).isEqualTo(a.workspaceId().toString());
        assertThat(id(created)).isNotEqualTo(a.adminId());
        assertThat(jdbcTemplate.queryForMap("SELECT role, workspace_id FROM users WHERE email = ?", email))
                .containsEntry("role", "MEMBER").containsEntry("workspace_id", a.workspaceId());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE workspace_id = ?", Integer.class,
                b.workspaceId())).isEqualTo(1);
    }

    @Test
    void emailsAreUniqueAcrossQueueFlowInAnyLetterCaseWithoutRevealingTheOwner() throws Exception {
        int users = userCount();
        List<String> taken = List.of(pedroEmail.toUpperCase(), "  " + pedroEmail + "  ", b.adminEmail(),
                b.adminEmail().toUpperCase());
        for (String email : taken) {
            MvcResult conflict = send(a.token(), post("/api/workspaces/{w}/members", a.workspaceId()), """
                    {"name": "Duplicate", "email": "%s", "password": "%s"}
                    """.formatted(email, PASSWORD));
            assertThat(status(conflict)).as(email).isEqualTo(409);
            assertThat(message(conflict)).isEqualTo("Email is already registered");
            assertThat(body(conflict)).doesNotContain(b.workspaceId().toString(), b.adminId().toString(), "bruno",
                    tag);
        }
        // And from the other side: B's ADMIN cannot take an address used in A.
        assertThat(status(send(b.token(), post("/api/workspaces/{w}/members", b.workspaceId()), """
                {"name": "Duplicate", "email": "%s", "password": "%s"}
                """.formatted(pedroEmail.toUpperCase(), PASSWORD)))).isEqualTo(409);
        assertThat(userCount()).isEqualTo(users);
    }

    /**
     * Deterministic race, as in DatabaseConflictRaceIntegrationTest: A
     * inserts the member and stays uncommitted; B, the real HTTP request for
     * the same address in another letter case, passes the duplicate
     * pre-check (A's row is invisible) and blocks on the unique index until
     * A commits. B is then a generic 409 and exactly one user exists.
     */
    @Test
    void concurrentCreationOfTheSameMemberEmailCreatesExactlyOneUser() throws Exception {
        String email = email("race");
        CountDownLatch aWritten = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        AuthenticatedUser ana = actorOf(userRepository.findById(a.adminId()).orElseThrow());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
            userService.createMember(ana, a.workspaceId(), new CreateMemberRequest("Racer A", email, PASSWORD));
            aWritten.countDown();
            try {
                assertThat(releaseA.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));
        assertThat(aWritten.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        Future<MvcResult> second = executor.submit(() -> send(a.token(),
                post("/api/workspaces/{w}/members", a.workspaceId()), """
                        {"name": "Racer B", "email": "%s", "password": "%s"}
                        """.formatted(email.toUpperCase(), PASSWORD)));
        awaitSessionBlockedOnLock();
        releaseA.countDown();
        first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        MvcResult result = second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertThat(status(result)).isEqualTo(409);
        assertThat(message(result)).isEqualTo("Resource conflicts with existing data");
        assertThat(body(result)).doesNotContain("uq_", "constraint", "duplicate key", "SQL", "Exception");
        assertThat(jdbcTemplate.queryForList("SELECT name FROM users WHERE lower(email) = ?", String.class, email))
                .containsExactly("Racer A");
    }

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
        throw new AssertionError("The second member creation never blocked on the first one's uncommitted row");
    }

    // ------------------------------------------------------------------
    // the created member is a real account; its role is the database's
    // ------------------------------------------------------------------

    @Test
    void theCreatedMemberLogsInAndIsAMemberOfTheAdminsWorkspace() throws Exception {
        MvcResult me = ok(pedroToken, get("/api/auth/me"), null);
        assertThat((String) read(me, "$.id")).isEqualTo(pedroId.toString());
        assertThat((String) read(me, "$.role")).isEqualTo("MEMBER");
        assertThat((String) read(me, "$.workspaceId")).isEqualTo(a.workspaceId().toString());

        // The token carries who, never what: no role or workspace claim to trust.
        String payload = new String(Base64.getUrlDecoder().decode(pedroToken.split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain("role", "ROLE", "ADMIN", "MEMBER", "workspace", "scope");

        assertStatus(401, null, post("/api/auth/login"), """
                {"email": "%s", "password": "wrong-password"}
                """.formatted(pedroEmail));
    }

    @Test
    void theCurrentDatabaseRoleDecidesEachRequestWithTheSameToken() throws Exception {
        String createOps = "{\"name\": \"Operations\", \"key\": \"OPS\"}";
        assertStatus(403, pedroToken, post("/api/projects"), createOps);

        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", pedroId);
        assertStatus(201, pedroToken, post("/api/projects"), createOps);

        jdbcTemplate.update("UPDATE users SET role = 'MEMBER' WHERE id = ?", pedroId);
        assertStatus(403, pedroToken, patch("/api/projects/{p}", a.projectId()), "{\"name\": \"Mine\"}");

        // Demoting the registering ADMIN takes effect on their very next request too.
        jdbcTemplate.update("UPDATE users SET role = 'MEMBER' WHERE id = ?", a.adminId());
        assertStatus(403, a.token(), post("/api/workspaces/{w}/members", a.workspaceId()), """
                {"name": "Late", "email": "%s", "password": "%s"}
                """.formatted(email("late"), PASSWORD));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", a.adminId());

        // The workspace is the database's too: moved to B, the same token now
        // sees B as its own workspace and A as not found - then back again.
        jdbcTemplate.update("UPDATE users SET workspace_id = ? WHERE id = ?", b.workspaceId(), pedroId);
        MvcResult moved = ok(pedroToken, get("/api/auth/me"), null);
        assertThat((String) read(moved, "$.workspaceId")).isEqualTo(b.workspaceId().toString());
        assertStatus(200, pedroToken, get("/api/workspaces/{w}", b.workspaceId()), null);
        assertStatus(404, pedroToken, get("/api/projects/{p}", a.projectId()), null);
        jdbcTemplate.update("UPDATE users SET workspace_id = ? WHERE id = ?", a.workspaceId(), pedroId);
        assertStatus(404, pedroToken, get("/api/workspaces/{w}", b.workspaceId()), null);
        assertStatus(200, pedroToken, get("/api/projects/{p}", a.projectId()), null);
    }

    /** A validly signed token claiming ADMIN in every common way changes nothing: the claims are never read. */
    @Test
    void roleClaimsInATokenAreIgnored() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(pedroId.toString())
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .claim("role", "ADMIN")
                .claim("roles", List.of("ADMIN", "ROLE_ADMIN"))
                .claim("authorities", List.of("ROLE_ADMIN"))
                .claim("scope", "ADMIN")
                .claim("workspaceId", b.workspaceId().toString())
                .claim("email", b.adminEmail())
                .claim("name", "Administrator")
                .build();
        String forged = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();

        MvcResult me = ok(forged, get("/api/auth/me"), null);
        assertThat((String) read(me, "$.id")).isEqualTo(pedroId.toString());
        assertThat((String) read(me, "$.email")).isEqualTo(pedroEmail);
        assertThat((String) read(me, "$.name")).isEqualTo("Pedro");
        assertThat((String) read(me, "$.role")).isEqualTo("MEMBER");
        assertThat((String) read(me, "$.workspaceId")).isEqualTo(a.workspaceId().toString());
        assertStatus(403, forged, post("/api/projects"), "{\"name\": \"Operations\", \"key\": \"OPS\"}");
        assertStatus(403, forged, post("/api/workspaces/{w}/members", a.workspaceId()), """
                {"name": "Forged", "email": "%s", "password": "%s"}
                """.formatted(email("forged"), PASSWORD));
        assertStatus(404, forged, post("/api/workspaces/{w}/members", b.workspaceId()), """
                {"name": "Forged", "email": "%s", "password": "%s"}
                """.formatted(email("forged"), PASSWORD));
    }

    // ------------------------------------------------------------------
    // a MEMBER still collaborates; authorship is not an ADMIN privilege
    // ------------------------------------------------------------------

    @Test
    void aMemberKeepsEveryNormalCollaborationOperation() throws Exception {
        ok(pedroToken, get("/api/workspaces/{w}", a.workspaceId()), null);
        ok(pedroToken, get("/api/workspaces/{w}/members", a.workspaceId()), null);
        ok(pedroToken, get("/api/users/{u}", a.adminId()), null);
        ok(pedroToken, get("/api/workspaces/{w}/projects", a.workspaceId()), null);
        ok(pedroToken, get("/api/projects/{p}", a.projectId()), null);
        ok(pedroToken, get("/api/projects/by-key").param("key", "ecom"), null);

        UUID ticket = id(ok(pedroToken, post("/api/tickets"), """
                {"projectId": "%s", "title": "Pedro's ticket", "status": "TODO", "priority": "LOW"}
                """.formatted(a.projectId())));
        ok(pedroToken, patch("/api/tickets/{t}", ticket), """
                {"status": "IN_PROGRESS", "priority": "HIGH", "assigneeId": "%s"}
                """.formatted(pedroId));
        ok(pedroToken, patch("/api/tickets/{t}", a.ticketId()), "{\"status\": \"REVIEW\"}");
        ok(pedroToken, get("/api/projects/{p}/tickets", a.projectId()), null);
        ok(pedroToken, get("/api/projects/{p}/tickets/1", a.projectId()), null);

        UUID label = id(ok(pedroToken, post("/api/labels"), "{\"name\": \"pedro-label\"}"));
        ok(pedroToken, get("/api/labels/{l}", label), null);
        ok(pedroToken, get("/api/labels/by-name").param("name", "pedro-label"), null);
        ok(pedroToken, get("/api/workspaces/{w}/labels", a.workspaceId()), null);
        ok(pedroToken, put("/api/tickets/{t}/labels/{l}", ticket, label), null);
        ok(pedroToken, delete("/api/tickets/{t}/labels/{l}", ticket, label), null);

        UUID comment = id(ok(pedroToken, post("/api/comments"), """
                {"ticketId": "%s", "content": "Pedro's comment"}
                """.formatted(ticket)));
        ok(pedroToken, get("/api/comments/{c}", comment), null);
        ok(pedroToken, get("/api/tickets/{t}/comments", ticket), null);
        ok(pedroToken, patch("/api/comments/{c}", comment), "{\"content\": \"Edited by Pedro\"}");
        ok(pedroToken, delete("/api/comments/{c}", comment), null);

        MvcResult activity = ok(pedroToken, get("/api/tickets/{t}/activities", ticket), null);
        assertThat((List<String>) read(activity, "$[*].type")).contains("TICKET_CREATED", "STATUS_CHANGED",
                "ASSIGNEE_CHANGED", "LABEL_ADDED", "LABEL_REMOVED");
        assertThat((List<String>) read(activity, "$[*].userId")).containsOnly(pedroId.toString());
    }

    @Test
    void anAdminCannotEditOrDeleteAnotherUsersCommentAndAForeignCommentIsNotFound() throws Exception {
        UUID pedrosComment = id(ok(pedroToken, post("/api/comments"), """
                {"ticketId": "%s", "content": "Pedro's words"}
                """.formatted(a.ticketId())));

        assertStatus(403, a.token(), patch("/api/comments/{c}", pedrosComment), "{\"content\": \"Ana's words\"}");
        assertStatus(403, a.token(), delete("/api/comments/{c}", pedrosComment), null);
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM comments WHERE id = ?", String.class,
                pedrosComment)).isEqualTo("Pedro's words");

        // Another MEMBER of the same workspace cannot either.
        String lucia = email("lucia");
        ok(a.token(), post("/api/workspaces/{w}/members", a.workspaceId()), """
                {"name": "Lucia", "email": "%s", "password": "%s"}
                """.formatted(lucia, PASSWORD));
        String luciaToken = login(lucia, PASSWORD);
        assertStatus(403, luciaToken, patch("/api/comments/{c}", pedrosComment), "{\"content\": \"Lucia's words\"}");
        assertStatus(403, luciaToken, delete("/api/comments/{c}", pedrosComment), null);
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM comments WHERE id = ?", String.class,
                pedrosComment)).isEqualTo("Pedro's words");

        // Nor the other way round, and for Ana's own comment Ana is the author.
        assertStatus(403, pedroToken, patch("/api/comments/{c}", a.commentId()), "{\"content\": \"x\"}");
        assertStatus(200, a.token(), patch("/api/comments/{c}", a.commentId()), "{\"content\": \"ana again\"}");

        assertStatus(404, a.token(), patch("/api/comments/{c}", b.commentId()), "{\"content\": \"x\"}");
        assertStatus(404, a.token(), delete("/api/comments/{c}", b.commentId()), null);
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM comments WHERE id = ?", String.class,
                b.commentId())).isEqualTo("bruno was here");

        assertStatus(200, pedroToken, patch("/api/comments/{c}", pedrosComment), "{\"content\": \"Pedro edited\"}");
        assertStatus(204, pedroToken, delete("/api/comments/{c}", pedrosComment), null);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String email(String name) {
        return tag + "-" + name + "@example.com";
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = ok(null, post("/api/auth/login"), """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password));
        return read(result, "$.accessToken");
    }

    private int userCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
    }

    private static void assertForbidden(MvcResult result, String message, String path) throws Exception {
        assertThat(status(result)).as(body(result)).isEqualTo(403);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat((Integer) read(result, "$.status")).isEqualTo(403);
        assertThat((String) read(result, "$.error")).isEqualTo("Forbidden");
        assertThat(message(result)).isEqualTo(message);
        assertThat((String) read(result, "$.path")).isEqualTo(path);
    }

    private MvcResult send(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult ok(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        MvcResult result = send(token, request, body);
        assertThat(status(result)).as(result.getRequest().getMethod() + " " + result.getRequest().getRequestURI()
                + " -> " + body(result)).isBetween(200, 299);
        return result;
    }

    private void assertStatus(int expected, String token, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        MvcResult result = send(token, request, body);
        assertThat(status(result)).as(result.getRequest().getMethod() + " " + result.getRequest().getRequestURI()
                + " -> " + body(result)).isEqualTo(expected);
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static String message(MvcResult result) throws Exception {
        return read(result, "$.message");
    }

    private static UUID id(MvcResult result) throws Exception {
        return UUID.fromString(read(result, "$.id"));
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
