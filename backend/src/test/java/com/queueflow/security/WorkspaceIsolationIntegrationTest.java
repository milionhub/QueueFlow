package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Multi-tenant isolation over the real stack: HTTP, security chain,
 * services, JPA and PostgreSQL. Two fully populated workspaces - A (Ana, the
 * ADMIN, and Pedro) and B (Bruno) - deliberately share a project key (ECOM)
 * and a label name (bug). Every request here uses a workspace-A token unless
 * stated otherwise; workspace B must behave exactly as if it did not exist.
 * No @Transactional; both workspaces are deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String tag = "iso-" + UUID.randomUUID().toString().substring(0, 8);

    /** Everything one workspace contains, created through the API. */
    private record Tenant(UUID workspaceId, UUID adminId, String adminEmail, String token, UUID projectId,
            UUID ticketId, UUID labelId, UUID commentId) {

        List<String> ids() {
            return List.of(workspaceId, adminId, projectId, ticketId, labelId, commentId).stream()
                    .map(UUID::toString).toList();
        }
    }

    private Tenant a;
    private Tenant b;
    private UUID pedroId;
    private String pedroToken;

    @BeforeEach
    void createTwoPopulatedWorkspaces() throws Exception {
        a = tenant("ana");
        b = tenant("bruno");
        User pedro = userRepository.save(new User("Pedro", tag + "-pedro@example.com", "hash", UserRole.MEMBER,
                workspaceRepository.findById(a.workspaceId()).orElseThrow()));
        pedroId = pedro.getId();
        pedroToken = accessTokenService.issue(pedroId).tokenValue();
    }

    private Tenant tenant(String name) throws Exception {
        String email = tag + "-" + name + "@example.com";
        MvcResult registered = ok(null, post("/api/auth/register"), """
                {"name": "%s", "email": "%s", "password": "iso-Pa55word", "workspaceName": "%s %s"}
                """.formatted(name, email, tag, name));
        String token = read(registered, "$.accessToken");
        UUID project = id(ok(token, post("/api/projects"), """
                {"name": "Shop", "key": "ECOM"}
                """));
        UUID ticket = id(ok(token, post("/api/tickets"), """
                {"projectId": "%s", "title": "%s ticket", "status": "TODO", "priority": "LOW"}
                """.formatted(project, name)));
        UUID label = id(ok(token, post("/api/labels"), """
                {"name": "bug"}
                """));
        ok(token, put("/api/tickets/{t}/labels/{l}", ticket, label), null);
        ok(token, patch("/api/tickets/{t}", ticket), """
                {"status": "IN_PROGRESS"}
                """);
        UUID comment = id(ok(token, post("/api/comments"), """
                {"ticketId": "%s", "content": "%s was here"}
                """.formatted(ticket, name)));
        return new Tenant(UUID.fromString(read(registered, "$.user.workspaceId")),
                UUID.fromString(read(registered, "$.user.id")), email, token, project, ticket, label, comment);
    }

    @AfterEach
    void cleanUp() {
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
    // collections
    // ------------------------------------------------------------------

    @Test
    void everyCollectionContainsOnlyTheCallersWorkspace() throws Exception {
        List<MvcResult> collections = List.of(
                ok(a.token(), get("/api/workspaces/{w}/members", a.workspaceId()), null),
                ok(a.token(), get("/api/workspaces/{w}/projects", a.workspaceId()), null),
                ok(a.token(), get("/api/workspaces/{w}/labels", a.workspaceId()), null),
                ok(a.token(), get("/api/projects/{p}/tickets", a.projectId()), null),
                ok(a.token(), get("/api/tickets/{t}/comments", a.ticketId()), null),
                ok(a.token(), get("/api/tickets/{t}/activities", a.ticketId()), null));

        for (MvcResult collection : collections) {
            String body = collection.getResponse().getContentAsString();
            assertThat((List<?>) JsonPath.read(body, "$")).as(collection.getRequest().getRequestURI()).isNotEmpty();
            for (String foreignId : b.ids()) {
                assertThat(body).as(collection.getRequest().getRequestURI()).doesNotContain(foreignId);
            }
            assertThat(body).doesNotContain(b.adminEmail(), "bruno");
        }
        assertThat((List<String>) read(collections.get(0), "$[*].name")).containsExactly("ana", "Pedro");
    }

    // ------------------------------------------------------------------
    // direct object references (IDOR)
    // ------------------------------------------------------------------

    @Test
    void foreignResourcesAreNotFoundExactlyLikeAbsentOnes() throws Exception {
        Map<String, Function<UUID, MockHttpServletRequestBuilder>> byWorkspace = Map.of(
                "workspace", w -> get("/api/workspaces/{w}", w),
                "members", w -> get("/api/workspaces/{w}/members", w),
                "projects", w -> get("/api/workspaces/{w}/projects", w),
                "labels", w -> get("/api/workspaces/{w}/labels", w));
        byWorkspace.forEach((name, request) -> assertLikeAbsent(name, request, b.workspaceId()));

        assertLikeAbsent("user", id -> get("/api/users/{u}", id), b.adminId());
        assertLikeAbsent("project", id -> get("/api/projects/{p}", id), b.projectId());
        assertLikeAbsent("project update", id -> json(patch("/api/projects/{p}", id), "{\"name\": \"Hijacked\"}"),
                b.projectId());
        assertLikeAbsent("project tickets", id -> get("/api/projects/{p}/tickets", id), b.projectId());
        assertLikeAbsent("ticket by number", id -> get("/api/projects/{p}/tickets/1", id), b.projectId());
        assertLikeAbsent("ticket", id -> get("/api/tickets/{t}", id), b.ticketId());
        assertLikeAbsent("ticket update", id -> json(patch("/api/tickets/{t}", id), "{\"status\": \"DONE\"}"),
                b.ticketId());
        assertLikeAbsent("ticket comments", id -> get("/api/tickets/{t}/comments", id), b.ticketId());
        assertLikeAbsent("ticket activity", id -> get("/api/tickets/{t}/activities", id), b.ticketId());
        assertLikeAbsent("label", id -> get("/api/labels/{l}", id), b.labelId());
        assertLikeAbsent("comment", id -> get("/api/comments/{c}", id), b.commentId());
        assertLikeAbsent("comment update", id -> json(patch("/api/comments/{c}", id), "{\"content\": \"x\"}"),
                b.commentId());
        assertLikeAbsent("comment delete", id -> delete("/api/comments/{c}", id), b.commentId());

        // Nothing of B changed.
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM projects WHERE id = ?", String.class, b.projectId()))
                .isEqualTo("Shop");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, b.ticketId()))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM comments WHERE id = ?", String.class,
                b.commentId())).isEqualTo("bruno was here");
    }

    @Test
    void lookupsByEmailKeyAndNameOnlySearchTheCallersWorkspace() throws Exception {
        MvcResult foreignEmail = send(a.token(), get("/api/users/by-email").param("email", b.adminEmail()), null);
        MvcResult absentEmail = send(a.token(), get("/api/users/by-email").param("email", tag + "-nobody@example.com"),
                null);
        assertThat(foreignEmail.getResponse().getStatus()).isEqualTo(404);
        assertThat(absentEmail.getResponse().getStatus()).isEqualTo(404);
        assertThat(message(foreignEmail).replace(b.adminEmail(), "<email>"))
                .isEqualTo(message(absentEmail).replace(tag + "-nobody@example.com", "<email>"));
        assertThat((String) read(ok(a.token(), get("/api/users/by-email").param("email", tag + "-pedro@example.com"),
                null), "$.id")).isEqualTo(pedroId.toString());

        // Both workspaces have a project ECOM and a label "bug": each caller finds their own.
        assertThat((String) read(ok(a.token(), get("/api/projects/by-key").param("key", "ecom"), null), "$.id"))
                .isEqualTo(a.projectId().toString());
        assertThat((String) read(ok(b.token(), get("/api/projects/by-key").param("key", "ecom"), null), "$.id"))
                .isEqualTo(b.projectId().toString());
        assertThat((String) read(ok(a.token(), get("/api/labels/by-name").param("name", "bug"), null), "$.id"))
                .isEqualTo(a.labelId().toString());

        // A key or name that only exists in B is not found for A.
        ok(b.token(), post("/api/projects"), "{\"name\": \"Only B\", \"key\": \"ONLYB\"}");
        ok(b.token(), post("/api/labels"), "{\"name\": \"only-b\"}");
        assertThat(send(a.token(), get("/api/projects/by-key").param("key", "ONLYB"), null).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(send(a.token(), get("/api/labels/by-name").param("name", "only-b"), null).getResponse().getStatus())
                .isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // relationship attacks
    // ------------------------------------------------------------------

    @Test
    void foreignResourcesCannotBeRelatedAndRejectedWritesChangeNothing() throws Exception {
        Map<String, Object> before = counts();
        UUID randomId = UUID.randomUUID();

        // Ticket under B's project; ticket assigned to B's user (create and update).
        assertStatus(404, a.token(), post("/api/tickets"), """
                {"projectId": "%s", "title": "x", "status": "TODO", "priority": "LOW"}
                """.formatted(b.projectId()));
        assertStatus(404, a.token(), post("/api/tickets"), """
                {"projectId": "%s", "title": "x", "status": "TODO", "priority": "LOW", "assigneeId": "%s"}
                """.formatted(a.projectId(), b.adminId()));
        assertStatus(404, a.token(), patch("/api/tickets/{t}", a.ticketId()), """
                {"status": "DONE", "assigneeId": "%s"}
                """.formatted(b.adminId()));

        // Labels: own ticket + foreign label, foreign ticket + own label, both foreign.
        String path = "/api/tickets/{t}/labels/{l}";
        MvcResult foreignLabel = send(a.token(), put(path, a.ticketId(), b.labelId()), null);
        MvcResult absentLabel = send(a.token(), put(path, a.ticketId(), randomId), null);
        assertThat(foreignLabel.getResponse().getStatus()).isEqualTo(404);
        assertThat(message(foreignLabel).replace(b.labelId().toString(), "<id>"))
                .isEqualTo(message(absentLabel).replace(randomId.toString(), "<id>"));
        assertStatus(404, a.token(), put(path, b.ticketId(), a.labelId()), null);
        assertStatus(404, a.token(), put(path, b.ticketId(), b.labelId()), null);
        assertStatus(404, a.token(), delete(path, b.ticketId(), b.labelId()), null);

        // Comment on B's ticket.
        assertStatus(404, a.token(), post("/api/comments"), """
                {"ticketId": "%s", "content": "x"}
                """.formatted(b.ticketId()));

        // Zero side effects, in both workspaces.
        assertThat(counts()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, a.ticketId()))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject("SELECT assignee_id FROM tickets WHERE id = ?", UUID.class,
                a.ticketId())).isNull();
    }

    // ------------------------------------------------------------------
    // 403 inside the workspace, 404 outside
    // ------------------------------------------------------------------

    @Test
    void sameWorkspaceOwnershipIsForbiddenWhileAForeignCommentIsNotFound() throws Exception {
        UUID pedrosComment = id(ok(pedroToken, post("/api/comments"), """
                {"ticketId": "%s", "content": "Pedro's words"}
                """.formatted(a.ticketId())));

        // Visible to Ana (same workspace) but not hers: 403.
        assertStatus(403, a.token(), patch("/api/comments/{c}", pedrosComment), "{\"content\": \"x\"}");
        assertStatus(403, a.token(), delete("/api/comments/{c}", pedrosComment), null);
        // Bruno's comment in workspace B: invisible to Ana, 404.
        assertStatus(404, a.token(), patch("/api/comments/{c}", b.commentId()), "{\"content\": \"x\"}");
        assertStatus(404, a.token(), delete("/api/comments/{c}", b.commentId()), null);

        assertThat(jdbcTemplate.queryForObject("SELECT content FROM comments WHERE id = ?", String.class,
                pedrosComment)).isEqualTo("Pedro's words");
    }

    // ------------------------------------------------------------------
    // keys, numbering and tenant selection
    // ------------------------------------------------------------------

    @Test
    void projectKeysAreUniquePerWorkspaceAndTicketNumbersPerProject() throws Exception {
        // Same key in both workspaces is fine; a second ECOM in A is a conflict.
        assertStatus(409, a.token(), post("/api/projects"), "{\"name\": \"Another\", \"key\": \"ecom\"}");

        MvcResult second = ok(a.token(), post("/api/tickets"), """
                {"projectId": "%s", "title": "Second", "status": "TODO", "priority": "LOW"}
                """.formatted(a.projectId()));
        assertThat((String) read(second, "$.displayKey")).isEqualTo("ECOM-2");
        assertThat((String) read(ok(b.token(), get("/api/tickets/{t}", b.ticketId()), null), "$.displayKey"))
                .isEqualTo("ECOM-1");
        assertThat(jdbcTemplate.queryForObject("SELECT next_ticket_number FROM projects WHERE id = ?", Long.class,
                b.projectId())).isEqualTo(2L);
    }

    @Test
    void theClientCannotChooseAnotherWorkspace() throws Exception {
        MvcResult project = ok(a.token(), post("/api/projects"), """
                {"workspaceId": "%s", "name": "Sneaky", "key": "SNKY"}
                """.formatted(b.workspaceId()));
        MvcResult label = ok(a.token(), post("/api/labels"), """
                {"workspaceId": "%s", "name": "sneaky"}
                """.formatted(b.workspaceId()));

        assertThat((String) read(project, "$.workspaceId")).isEqualTo(a.workspaceId().toString());
        assertThat((String) read(label, "$.workspaceId")).isEqualTo(a.workspaceId().toString());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM projects WHERE workspace_id = ?", Integer.class,
                b.workspaceId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM labels WHERE workspace_id = ?", Integer.class,
                b.workspaceId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------

    /** 404 for the foreign id, with the same body as for a random id (apart from the id itself). */
    private void assertLikeAbsent(String what, Function<UUID, MockHttpServletRequestBuilder> request, UUID foreignId) {
        try {
            UUID randomId = UUID.randomUUID();
            MvcResult foreign = send(a.token(), request.apply(foreignId), null);
            MvcResult absent = send(a.token(), request.apply(randomId), null);
            assertThat(foreign.getResponse().getStatus()).as(what).isEqualTo(404);
            assertThat(absent.getResponse().getStatus()).as(what).isEqualTo(404);
            assertThat(message(foreign).replace(foreignId.toString(), "<id>")).as(what)
                    .isEqualTo(message(absent).replace(randomId.toString(), "<id>"));
            for (String id : b.ids()) {
                if (!id.equals(foreignId.toString())) {
                    assertThat(foreign.getResponse().getContentAsString()).as(what).doesNotContain(id);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> counts() {
        return jdbcTemplate.queryForMap("SELECT (SELECT count(*) FROM tickets) AS tickets, "
                + "(SELECT count(*) FROM ticket_labels) AS ticket_labels, (SELECT count(*) FROM comments) AS comments, "
                + "(SELECT count(*) FROM activities) AS activities, "
                + "(SELECT sum(next_ticket_number) FROM projects) AS counters");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MvcResult send(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        if (body != null) {
            json(request, body);
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult ok(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        MvcResult result = send(token, request, body);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isBetween(200, 299);
        return result;
    }

    private void assertStatus(int expected, String token, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        MvcResult result = send(token, request, body);
        assertThat(result.getResponse().getStatus()).as(result.getRequest().getMethod() + " "
                + result.getRequest().getRequestURI() + " -> " + result.getResponse().getContentAsString())
                .isEqualTo(expected);
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
