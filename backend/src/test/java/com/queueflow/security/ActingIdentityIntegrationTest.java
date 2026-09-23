package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
 * The acting user is whoever the access token identifies - nothing a client
 * sends can change it. Juan (ADMIN) and Pedro (MEMBER) share a workspace;
 * every request below is Juan's, and several try to act as Pedro through the
 * old identity inputs (creatorId, authorId, ?actorUserId=). The database
 * shows who was actually recorded. Real HTTP, security chain and PostgreSQL;
 * no @Transactional, so everything is cleaned up afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActingIdentityIntegrationTest {

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

    private final String tag = "actid-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<UUID> workspaces = new ArrayList<>();

    private UUID juanId;
    private String juanToken;
    private UUID pedroId;
    private String pedroToken;
    private UUID workspaceId;
    private UUID projectId;
    private UUID ticketId;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult juan = perform(null, post("/api/auth/register"), """
                {"name": "Juan", "email": "%s-juan@example.com", "password": "juan-Pa55word",
                 "workspaceName": "%s"}
                """.formatted(tag, tag));
        juanId = UUID.fromString(read(juan, "$.user.id"));
        juanToken = read(juan, "$.accessToken");
        workspaceId = UUID.fromString(read(juan, "$.user.workspaceId"));
        workspaces.add(workspaceId);

        // Pedro joins the same workspace (no member endpoint yet) and gets his own token.
        User pedro = userRepository.save(new User("Pedro", tag + "-pedro@example.com", "hash", UserRole.MEMBER,
                workspaceRepository.findById(workspaceId).orElseThrow()));
        pedroId = pedro.getId();
        pedroToken = accessTokenService.issue(pedroId).tokenValue();

        projectId = UUID.fromString(read(perform(juanToken, post("/api/projects"), """
                {"name": "Identity", "key": "IDN"}
                """), "$.id"));
        ticketId = UUID.fromString(read(perform(juanToken, post("/api/tickets"), """
                {"projectId": "%s", "title": "Setup ticket", "status": "TODO", "priority": "LOW"}
                """.formatted(projectId)), "$.id"));
    }

    @AfterEach
    void cleanUp() {
        for (UUID w : workspaces) {
            String tickets =
                    "SELECT t.id FROM tickets t JOIN projects p ON p.id = t.project_id WHERE p.workspace_id = ?";
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
    // Juan cannot act as Pedro
    // ------------------------------------------------------------------

    @Test
    void ticketCreatorIsTheTokensUserWhateverCreatorIdIsSent() throws Exception {
        MvcResult created = perform(juanToken, post("/api/tickets"), """
                {"projectId": "%s", "title": "Juan's ticket", "status": "TODO", "priority": "HIGH",
                 "creatorId": "%s"}
                """.formatted(projectId, pedroId));
        UUID id = UUID.fromString(read(created, "$.id"));

        assertThat((String) read(created, "$.creatorId")).isEqualTo(juanId.toString());
        assertThat(uuid("SELECT creator_id FROM tickets WHERE id = ?", id)).isEqualTo(juanId);
        assertThat(uuid("SELECT user_id FROM activities WHERE ticket_id = ? AND type = 'TICKET_CREATED'", id))
                .isEqualTo(juanId);
    }

    @Test
    void commentAuthorIsTheTokensUserWhateverAuthorIdIsSent() throws Exception {
        MvcResult comment = perform(juanToken, post("/api/comments"), """
                {"ticketId": "%s", "authorId": "%s", "content": "Written by Juan"}
                """.formatted(ticketId, pedroId));

        assertThat((String) read(comment, "$.authorId")).isEqualTo(juanId.toString());
        assertThat(uuid("SELECT author_id FROM comments WHERE id = ?", UUID.fromString(read(comment, "$.id"))))
                .isEqualTo(juanId);
    }

    @Test
    void ticketUpdateActivityIsAttributedToTheTokensUser() throws Exception {
        perform(juanToken, patch("/api/tickets/{id}", ticketId).param("actorUserId", pedroId.toString()), """
                {"status": "IN_PROGRESS", "priority": "CRITICAL"}
                """);

        assertThat(jdbcTemplate.queryForList(
                "SELECT user_id FROM activities WHERE ticket_id = ? "
                        + "AND type IN ('STATUS_CHANGED', 'PRIORITY_CHANGED')",
                UUID.class, ticketId)).containsExactly(juanId, juanId);
    }

    @Test
    void labelActivityIsAttributedToTheTokensUser() throws Exception {
        UUID labelId = UUID.fromString(read(perform(juanToken, post("/api/labels"), """
                {"name": "urgent"}
                """), "$.id"));
        String path = "/api/tickets/{ticketId}/labels/{labelId}";

        perform(juanToken, put(path, ticketId, labelId).param("actorUserId", pedroId.toString()), null);
        perform(juanToken, delete(path, ticketId, labelId).param("actorUserId", pedroId.toString()), null);

        assertThat(jdbcTemplate.queryForList("SELECT type, user_id FROM activities WHERE ticket_id = ? "
                + "AND type IN ('LABEL_ADDED', 'LABEL_REMOVED') ORDER BY created_at, id", ticketId))
                .extracting(row -> row.get("type") + ":" + row.get("user_id"))
                .containsExactly("LABEL_ADDED:" + juanId, "LABEL_REMOVED:" + juanId);
    }

    @Test
    void juanCannotEditOrDeletePedrosCommentByNamingPedro() throws Exception {
        UUID pedrosComment = UUID.fromString(read(perform(pedroToken, post("/api/comments"), """
                {"ticketId": "%s", "content": "Pedro's words"}
                """.formatted(ticketId)), "$.id"));

        // Juan is an ADMIN: that does not help either (approved V1 policy).
        MvcResult edit = mockMvc.perform(withToken(juanToken, patch("/api/comments/{id}", pedrosComment))
                .param("actorUserId", pedroId.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"Juan's words\"}")).andReturn();
        MvcResult remove = mockMvc.perform(withToken(juanToken, delete("/api/comments/{id}", pedrosComment))
                .param("actorUserId", pedroId.toString())).andReturn();

        assertThat(edit.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) read(edit, "$.message")).isEqualTo("Only the comment author can edit this comment");
        assertThat(remove.getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM comments WHERE id = ?", String.class,
                pedrosComment)).isEqualTo("Pedro's words");

        // Pedro himself can.
        perform(pedroToken, patch("/api/comments/{id}", pedrosComment), """
                {"content": "Pedro's edited words"}
                """);
        assertThat(mockMvc.perform(withToken(pedroToken, delete("/api/comments/{id}", pedrosComment)))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
    }

    // ------------------------------------------------------------------
    // Another workspace's resources do not exist for this caller (404), and
    // writes against them change nothing. WorkspaceIsolationIntegrationTest
    // covers isolation in depth.
    // ------------------------------------------------------------------

    @Test
    void anotherWorkspaceCannotActOnTheseResources() throws Exception {
        MvcResult other = perform(null, post("/api/auth/register"), """
                {"name": "Olga", "email": "%s-olga@example.com", "password": "olga-Pa55word",
                 "workspaceName": "%s"}
                """.formatted(tag, tag));
        workspaces.add(UUID.fromString(read(other, "$.user.workspaceId")));
        String outsider = read(other, "$.accessToken");
        UUID labelId = UUID.fromString(read(perform(juanToken, post("/api/labels"), """
                {"name": "bug"}
                """), "$.id"));

        assertThat(status(outsider, post("/api/tickets"), """
                {"projectId": "%s", "title": "x", "status": "TODO", "priority": "LOW"}
                """.formatted(projectId))).isEqualTo(404);
        assertThat(status(outsider, patch("/api/tickets/{id}", ticketId), "{\"status\": \"DONE\"}")).isEqualTo(404);
        assertThat(status(outsider, put("/api/tickets/{t}/labels/{l}", ticketId, labelId), null)).isEqualTo(404);
        assertThat(status(outsider, post("/api/comments"), """
                {"ticketId": "%s", "content": "x"}
                """.formatted(ticketId))).isEqualTo(404);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tickets WHERE project_id = ?", Integer.class,
                projectId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticketId))
                .isEqualTo("TODO");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM comments WHERE ticket_id = ?", Integer.class,
                ticketId)).isZero();
    }

    // ------------------------------------------------------------------

    private static MockHttpServletRequestBuilder withToken(String token, MockHttpServletRequestBuilder request) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** Performs the request and requires a 2xx. */
    private MvcResult perform(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        MvcResult result = mockMvc.perform(withToken(token, request)).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isBetween(200, 299);
        return result;
    }

    private int status(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(withToken(token, request)).andReturn().getResponse().getStatus();
    }

    private UUID uuid(String sql, UUID id) {
        return jdbcTemplate.queryForObject(sql, UUID.class, id);
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
