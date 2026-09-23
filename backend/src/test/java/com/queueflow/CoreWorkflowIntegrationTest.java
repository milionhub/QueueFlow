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
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

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
 * Users are created through the repository: there is no user endpoint
 * until Phase 2 registration.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoreWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID workspaceId;

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
        MvcResult workspace = send(post("/api/workspaces"), """
                {"name": "Workflow Workspace"}
                """, 201);
        workspaceId = UUID.fromString(read(workspace, "$.id"));
        assertThat(workspace.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/workspaces/" + workspaceId);

        Workspace saved = workspaceRepository.findById(workspaceId).orElseThrow();
        User alice = userRepository.save(new User("Alice", "alice-" + workspaceId + "@example.com", "hash",
                UserRole.ADMIN, saved));
        User bob = userRepository.save(new User("bob", "bob-" + workspaceId + "@example.com", "hash",
                UserRole.MEMBER, saved));

        MvcResult members = call(get("/api/workspaces/{id}/members", workspaceId), 200);
        assertThat(read(members, "$[*].name").toString()).isEqualTo("[\"Alice\",\"bob\"]");
        assertThat(members.getResponse().getContentAsString()).doesNotContain("passwordHash");

        // --- projects: normalization + per-project numbering ----------------
        MvcResult core = send(post("/api/projects"), """
                {"workspaceId": "%s", "name": "  Core Platform  ", "key": " core "}
                """.formatted(workspaceId), 201);
        UUID coreId = UUID.fromString(read(core, "$.id"));
        assertThat((String) read(core, "$.key")).isEqualTo("CORE");
        assertThat((String) read(core, "$.name")).isEqualTo("Core Platform");
        UUID opsId = UUID.fromString(read(send(post("/api/projects"), """
                {"workspaceId": "%s", "name": "Operations", "key": "ops"}
                """.formatted(workspaceId), 201), "$.id"));

        String ticketJson = """
                {"projectId": "%s", "title": "%s", "description": "Initial description",
                 "status": "BACKLOG", "priority": "LOW", "creatorId": "%s"}
                """;
        MvcResult core1 = send(post("/api/tickets"), ticketJson.formatted(coreId, "Checkout bug", alice.getId()), 201);
        MvcResult ops1 = send(post("/api/tickets"), ticketJson.formatted(opsId, "Deploy", alice.getId()), 201);
        MvcResult core2 = send(post("/api/tickets"), ticketJson.formatted(coreId, "Search", alice.getId()), 201);
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
        MvcResult patched = send(patch("/api/tickets/{id}", ticketId).param("actorUserId", alice.getId().toString()),
                """
                {"title": "Checkout fails", "description": null, "status": "IN_PROGRESS",
                 "priority": "HIGH", "assigneeId": "%s"}
                """.formatted(bob.getId()), 200);
        assertThat((String) read(patched, "$.title")).isEqualTo("Checkout fails");
        assertThat((Object) read(patched, "$.description")).isNull();
        assertThat((String) read(patched, "$.status")).isEqualTo("IN_PROGRESS");
        assertThat((String) read(patched, "$.assigneeId")).isEqualTo(bob.getId().toString());

        // --- labels: attach (idempotent), ordered in the response, detach ---
        UUID urgent = UUID.fromString(read(send(post("/api/labels"), """
                {"workspaceId": "%s", "name": "urgent"}
                """.formatted(workspaceId), 201), "$.id"));
        UUID bug = UUID.fromString(read(send(post("/api/labels"), """
                {"workspaceId": "%s", "name": "Bug"}
                """.formatted(workspaceId), 201), "$.id"));
        String labelPath = "/api/tickets/{ticketId}/labels/{labelId}";
        call(put(labelPath, ticketId, urgent).param("actorUserId", alice.getId().toString()), 200);
        MvcResult labelled = call(put(labelPath, ticketId, bug).param("actorUserId", alice.getId().toString()), 200);
        assertThat(read(labelled, "$.labels[*].name").toString()).isEqualTo("[\"Bug\",\"urgent\"]");
        call(put(labelPath, ticketId, bug).param("actorUserId", alice.getId().toString()), 200);
        MvcResult unlabelled = call(delete(labelPath, ticketId, urgent).param("actorUserId", alice.getId().toString()), 200);
        assertThat(read(unlabelled, "$.labels[*].name").toString()).isEqualTo("[\"Bug\"]");

        // --- comments: author name resolved outside any open session --------
        MvcResult comment = send(post("/api/comments"), """
                {"ticketId": "%s", "authorId": "%s", "content": "Looking into it"}
                """.formatted(ticketId, bob.getId()), 201);
        assertThat((String) read(comment, "$.authorName")).isEqualTo("bob");
        assertThat(read(call(get("/api/tickets/{id}/comments", ticketId), 200), "$[*].content").toString())
                .isEqualTo("[\"Looking into it\"]");

        // --- activity: all eight types, in order, with actor and values -----
        MvcResult activity = call(get("/api/tickets/{id}/activities", ticketId), 200);
        List<Map<String, Object>> entries = read(activity, "$[*]");
        assertThat(entries).extracting(e -> e.get("type")).containsExactly(
                "TICKET_CREATED", "TITLE_CHANGED", "DESCRIPTION_CHANGED", "STATUS_CHANGED", "PRIORITY_CHANGED",
                "ASSIGNEE_CHANGED", "LABEL_ADDED", "LABEL_ADDED", "LABEL_REMOVED");
        assertThat(entries).extracting(e -> e.get("oldValue")).containsExactly(
                null, "Checkout bug", "Initial description", "BACKLOG", "LOW", null, null, null, "urgent");
        assertThat(entries).extracting(e -> e.get("newValue")).containsExactly(
                null, "Checkout fails", null, "IN_PROGRESS", "HIGH", bob.getId().toString(), "urgent", "Bug", null);
        assertThat(entries).extracting(e -> e.get("userName")).containsOnly("Alice");
        assertThat(entries).extracting(e -> e.get("ticketId")).containsOnly(ticketId.toString());

        // --- final state and the project counters ---------------------------
        MvcResult finalTicket = call(get("/api/tickets/{id}", ticketId), 200);
        assertThat(read(finalTicket, "$.labels[*].name").toString()).isEqualTo("[\"Bug\"]");
        assertThat((String) read(finalTicket, "$.assigneeId")).isEqualTo(bob.getId().toString());
        assertThat(jdbcTemplate.queryForObject("SELECT next_ticket_number FROM projects WHERE id = ?", Long.class,
                coreId)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("SELECT next_ticket_number FROM projects WHERE id = ?", Long.class,
                opsId)).isEqualTo(2L);
        assertThat(read(call(get("/api/workspaces/{id}/projects", workspaceId), 200), "$[*].key").toString())
                .isEqualTo("[\"CORE\",\"OPS\"]");
    }
}
