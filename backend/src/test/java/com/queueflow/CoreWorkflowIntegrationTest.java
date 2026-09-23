package com.queueflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

/**
 * One connected Phase 1 workflow through the real application: HTTP ->
 * controllers -> services -> JPA -> PostgreSQL and back to JSON, with no
 * mocks and no test-managed transaction.
 *
 * What only this test proves:
 * <ul>
 *   <li>Production wiring and JSON serialization with real services (the
 *       controller tests mock the services).</li>
 *   <li>Every response is built inside its service transaction: with
 *       open-in-view=false there is no session during serialization, and
 *       the @DataJpaTest integration tests cannot show this because their
 *       test transaction keeps the session open.</li>
 *   <li>All eight ActivityTypes are persisted through the real database
 *       CHECK constraint, with correct actor and old/new values.</li>
 *   <li>Ticket numbers are allocated per project.</li>
 * </ul>
 * The workspace and its ADMIN come from real registration; the ADMIN then
 * creates the second member, who logs in with their own password. The full
 * role matrix lives in RolePolicyIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoreWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID workspaceId;

    /** Alice's access token, sent on every request once she has logged in. */
    private String accessToken;

    @AfterEach
    void cleanUp() {
        if (workspaceId == null) {
            return;
        }
        String tickets = "SELECT t.id FROM tickets t JOIN projects p ON p.id = t.project_id WHERE p.workspace_id = ?";
        jdbcTemplate.update("DELETE FROM activities WHERE ticket_id IN (" + tickets + ")", workspaceId);
        jdbcTemplate.update("DELETE FROM comments WHERE ticket_id IN (" + tickets + ")", workspaceId);
        jdbcTemplate.update("DELETE FROM ticket_labels WHERE ticket_id IN (" + tickets + ")", workspaceId);
        jdbcTemplate.update("DELETE FROM tickets WHERE id IN (" + tickets + ")", workspaceId);
        jdbcTemplate.update("DELETE FROM labels WHERE workspace_id = ?", workspaceId);
        jdbcTemplate.update("DELETE FROM projects WHERE workspace_id = ?", workspaceId);
        jdbcTemplate.update("DELETE FROM users WHERE workspace_id = ?", workspaceId);
        jdbcTemplate.update("DELETE FROM workspaces WHERE id = ?", workspaceId);
    }

    private MvcResult call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("%s %s -> %s", result.getRequest().getMethod(), result.getRequest().getRequestURI(),
                        result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return result;
    }

    private MvcResult send(MockHttpServletRequestBuilder request, String json, int expectedStatus) throws Exception {
        return call(request.contentType(MediaType.APPLICATION_JSON).content(json), expectedStatus);
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    @Test
    void coreWorkflowPersistsServesAndAuditsEverything() throws Exception {
        // --- workspace + members --------------------------------------------
        // Registration creates the workspace and its first user, Alice (ADMIN).
        String aliceEmail = "Alice-" + UUID.randomUUID() + "@Example.com";
        MvcResult registered = send(post("/api/auth/register"), """
                {"name": "Alice", "email": "%s", "password": "workflow-password",
                 "workspaceName": "Workflow Workspace"}
                """.formatted(aliceEmail), 201);
        workspaceId = UUID.fromString(read(registered, "$.user.workspaceId"));
        UUID aliceId = UUID.fromString(read(registered, "$.user.id"));
        assertThat((String) read(registered, "$.user.role")).isEqualTo("ADMIN");
        assertThat(registered.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/users/" + aliceId);

        // Every other endpoint needs her access token: log in, then use it throughout.
        call(get("/api/workspaces/{id}", workspaceId), 401);
        accessToken = read(send(post("/api/auth/login"), """
                {"email": "%s", "password": "workflow-password"}
                """.formatted(aliceEmail), 200), "$.accessToken");
        assertThat((String) read(call(get("/api/auth/me"), 200), "$.id")).isEqualTo(aliceId.toString());
        assertThat((String) read(call(get("/api/workspaces/{id}", workspaceId), 200), "$.name"))
                .isEqualTo("Workflow Workspace");

        // Alice (ADMIN) adds Bob; he is always a MEMBER and gets no token here.
        String bobEmail = "bob-" + workspaceId + "@example.com";
        MvcResult bobCreated = send(post("/api/workspaces/{id}/members", workspaceId), """
                {"name": "bob", "email": "%s", "password": "bob-password"}
                """.formatted(bobEmail), 201);
        assertThat((String) read(bobCreated, "$.role")).isEqualTo("MEMBER");
        UUID bobId = UUID.fromString(read(bobCreated, "$.id"));

        MvcResult members = call(get("/api/workspaces/{id}/members", workspaceId), 200);
        assertThat(read(members, "$[*].name").toString()).isEqualTo("[\"Alice\",\"bob\"]");
        assertThat(members.getResponse().getContentAsString()).doesNotContain("passwordHash");

        // --- projects: normalization + per-project numbering ----------------
        MvcResult core = send(post("/api/projects"), """
                {"name": "  Core Platform  ", "key": " core "}
                """, 201);
        UUID coreId = UUID.fromString(read(core, "$.id"));
        assertThat((String) read(core, "$.key")).isEqualTo("CORE");
        assertThat((String) read(core, "$.name")).isEqualTo("Core Platform");
        UUID opsId = UUID.fromString(read(send(post("/api/projects"), """
                {"name": "Operations", "key": "ops"}
                """, 201), "$.id"));

        // No creator in the request: the token's user creates the ticket.
        String ticketJson = """
                {"projectId": "%s", "title": "%s", "description": "Initial description",
                 "status": "BACKLOG", "priority": "LOW"}
                """;
        MvcResult core1 = send(post("/api/tickets"), ticketJson.formatted(coreId, "Checkout bug"), 201);
        MvcResult ops1 = send(post("/api/tickets"), ticketJson.formatted(opsId, "Deploy"), 201);
        MvcResult core2 = send(post("/api/tickets"), ticketJson.formatted(coreId, "Search"), 201);
        assertThat((String) read(core1, "$.creatorId")).isEqualTo(aliceId.toString());
        assertThat((String) read(core1, "$.displayKey")).isEqualTo("CORE-1");
        assertThat((String) read(ops1, "$.displayKey")).isEqualTo("OPS-1");
        assertThat((String) read(core2, "$.displayKey")).isEqualTo("CORE-2");
        UUID ticketId = UUID.fromString(read(core1, "$.id"));
        assertThat(core1.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/tickets/" + ticketId);

        assertThat(read(call(get("/api/projects/{id}/tickets", coreId), 200), "$[*].displayKey").toString())
                .isEqualTo("[\"CORE-1\",\"CORE-2\"]");
        assertThat((String) read(call(get("/api/projects/{id}/tickets/{n}", coreId, 2), 200), "$.title"))
                .isEqualTo("Search");

        // --- PATCH every field kind (explicit null clears description) ------
        MvcResult patched = send(patch("/api/tickets/{id}", ticketId), """
                {"title": "Checkout fails", "description": null, "status": "IN_PROGRESS",
                 "priority": "HIGH", "assigneeId": "%s"}
                """.formatted(bobId), 200);
        assertThat((String) read(patched, "$.title")).isEqualTo("Checkout fails");
        assertThat((Object) read(patched, "$.description")).isNull();
        assertThat((String) read(patched, "$.status")).isEqualTo("IN_PROGRESS");
        assertThat((String) read(patched, "$.assigneeId")).isEqualTo(bobId.toString());

        // --- labels: attach (idempotent), ordered in the response, detach ---
        UUID urgent = UUID.fromString(read(send(post("/api/labels"), """
                {"name": "urgent"}
                """, 201), "$.id"));
        UUID bug = UUID.fromString(read(send(post("/api/labels"), """
                {"name": "Bug"}
                """, 201), "$.id"));
        String labelPath = "/api/tickets/{ticketId}/labels/{labelId}";
        call(put(labelPath, ticketId, urgent), 200);
        MvcResult labelled = call(put(labelPath, ticketId, bug), 200);
        assertThat(read(labelled, "$.labels[*].name").toString()).isEqualTo("[\"Bug\",\"urgent\"]");
        call(put(labelPath, ticketId, bug), 200);
        MvcResult unlabelled = call(delete(labelPath, ticketId, urgent), 200);
        assertThat(read(unlabelled, "$.labels[*].name").toString()).isEqualTo("[\"Bug\"]");

        // --- comments: the token's user is the author; only they may edit/delete
        // (author name resolved outside any open session) --------------------
        MvcResult comment = send(post("/api/comments"), """
                {"ticketId": "%s", "content": "Looking into it"}
                """.formatted(ticketId), 201);
        UUID commentId = UUID.fromString(read(comment, "$.id"));
        assertThat((String) read(comment, "$.authorId")).isEqualTo(aliceId.toString());
        assertThat((String) read(comment, "$.authorName")).isEqualTo("Alice");
        assertThat((String) read(send(patch("/api/comments/{id}", commentId), """
                {"content": "Looking into it now"}
                """, 200), "$.content")).isEqualTo("Looking into it now");

        // Bob logs in himself and cannot touch Alice's comment; his own is his.
        String aliceToken = accessToken;
        accessToken = read(send(post("/api/auth/login"), """
                {"email": "%s", "password": "bob-password"}
                """.formatted(bobEmail), 200), "$.accessToken");
        send(patch("/api/comments/{id}", commentId), """
                {"content": "Hijacked"}
                """, 403);
        call(delete("/api/comments/{id}", commentId), 403);
        MvcResult bobsComment = send(post("/api/comments"), """
                {"ticketId": "%s", "content": "Me too"}
                """.formatted(ticketId), 201);
        assertThat((String) read(bobsComment, "$.authorId")).isEqualTo(bobId.toString());
        assertThat((String) read(bobsComment, "$.authorName")).isEqualTo("bob");
        accessToken = aliceToken;

        call(delete("/api/comments/{id}", commentId), 204);
        assertThat(read(call(get("/api/tickets/{id}/comments", ticketId), 200), "$[*].content").toString())
                .isEqualTo("[\"Me too\"]");

        // --- activity: all eight types, in order, with actor and values -----
        MvcResult activity = call(get("/api/tickets/{id}/activities", ticketId), 200);
        List<Map<String, Object>> entries = read(activity, "$[*]");
        assertThat(entries).extracting(e -> e.get("type")).containsExactly(
                "TICKET_CREATED", "TITLE_CHANGED", "DESCRIPTION_CHANGED", "STATUS_CHANGED", "PRIORITY_CHANGED",
                "ASSIGNEE_CHANGED", "LABEL_ADDED", "LABEL_ADDED", "LABEL_REMOVED");
        assertThat(entries).extracting(e -> e.get("oldValue")).containsExactly(
                null, "Checkout bug", "Initial description", "BACKLOG", "LOW", null, null, null, "urgent");
        assertThat(entries).extracting(e -> e.get("newValue")).containsExactly(
                null, "Checkout fails", null, "IN_PROGRESS", "HIGH", bobId.toString(), "urgent", "Bug", null);
        assertThat(entries).extracting(e -> e.get("userId")).containsOnly(aliceId.toString());
        assertThat(entries).extracting(e -> e.get("userName")).containsOnly("Alice");
        assertThat(entries).extracting(e -> e.get("ticketId")).containsOnly(ticketId.toString());

        // --- final state and the project counters ---------------------------
        MvcResult finalTicket = call(get("/api/tickets/{id}", ticketId), 200);
        assertThat(read(finalTicket, "$.labels[*].name").toString()).isEqualTo("[\"Bug\"]");
        assertThat((String) read(finalTicket, "$.assigneeId")).isEqualTo(bobId.toString());
        assertThat(jdbcTemplate.queryForObject("SELECT next_ticket_number FROM projects WHERE id = ?", Long.class,
                coreId)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("SELECT next_ticket_number FROM projects WHERE id = ?", Long.class,
                opsId)).isEqualTo(2L);
        assertThat(read(call(get("/api/workspaces/{id}/projects", workspaceId), 200), "$[*].key").toString())
                .isEqualTo("[\"CORE\",\"OPS\"]");
    }
}
