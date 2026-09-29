package com.queueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
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

/**
 * Editing and removing members over the real stack: HTTP, real access
 * tokens, the security chain, services, JPA and PostgreSQL.
 *
 * Workspace A: Ana (ADMIN), who adds Laura and Pedro as MEMBERs through the
 * API; both then log in with their own passwords. Laura takes part in the
 * work before anything is removed: she creates a ticket, is assigned to two,
 * comments and changes a status. Workspace B: Bruno (ADMIN). No
 * @Transactional; both workspaces are deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberManagementIntegrationTest {

    private static final String PASSWORD = "member-Pa55word";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String tag = "mbr-" + UUID.randomUUID().toString().substring(0, 8);

    private UUID workspaceA;
    private UUID workspaceB;
    private UUID anaId;
    private String anaToken;
    private String brunoToken;
    private UUID lauraId;
    private String lauraToken;
    private UUID pedroId;
    private String pedroToken;
    /** Created by Laura and assigned to her. */
    private UUID lauraTicket;
    /** Created by Ana and assigned to Laura. */
    private UUID anaTicket;
    private UUID lauraComment;

    @BeforeEach
    void createWorkspacesMembersAndWork() throws Exception {
        MvcResult ana = register("ana", "A");
        workspaceA = UUID.fromString(read(ana, "$.user.workspaceId"));
        anaId = UUID.fromString(read(ana, "$.user.id"));
        anaToken = read(ana, "$.accessToken");
        MvcResult bruno = register("bruno", "B");
        workspaceB = UUID.fromString(read(bruno, "$.user.workspaceId"));
        brunoToken = read(bruno, "$.accessToken");

        lauraId = addMember("Laura", email("laura"));
        lauraToken = login(email("laura"), PASSWORD);
        pedroId = addMember("Pedro", email("pedro"));
        pedroToken = login(email("pedro"), PASSWORD);

        UUID project = id(ok(anaToken, post("/api/projects"), """
                {"name": "Shop", "key": "ECOM"}
                """));
        lauraTicket = id(ok(lauraToken, post("/api/tickets"), """
                {"projectId": "%s", "title": "Laura's ticket", "status": "TODO", "priority": "LOW",
                 "assigneeId": "%s"}
                """.formatted(project, lauraId)));
        anaTicket = id(ok(anaToken, post("/api/tickets"), """
                {"projectId": "%s", "title": "Ana's ticket", "status": "TODO", "priority": "HIGH",
                 "assigneeId": "%s"}
                """.formatted(project, lauraId)));
        ok(lauraToken, patch("/api/tickets/{t}", anaTicket), """
                {"status": "IN_PROGRESS"}
                """);
        lauraComment = id(ok(lauraToken, post("/api/comments"), """
                {"ticketId": "%s", "content": "Laura was here"}
                """.formatted(anaTicket)));
    }

    @AfterEach
    void cleanUp() {
        for (UUID w : new UUID[] {workspaceA, workspaceB}) {
            if (w == null) {
                continue;
            }
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
    // edit
    // ------------------------------------------------------------------

    @Test
    void anAdminRenamesAMemberAndNoOtherFieldCanBeChanged() throws Exception {
        MvcResult result = ok(anaToken, patch("/api/workspaces/{w}/members/{u}", workspaceA, lauraId), """
                {"name": "  Laura García  ", "email": "%s", "password": "another-Pa55word", "role": "ADMIN",
                 "workspaceId": "%s", "id": "%s"}
                """.formatted(email("hijacked"), workspaceB, UUID.randomUUID()));

        assertThat((String) read(result, "$.id")).isEqualTo(lauraId.toString());
        assertThat((String) read(result, "$.name")).isEqualTo("Laura García");
        assertThat((String) read(result, "$.email")).isEqualTo(email("laura"));
        assertThat((String) read(result, "$.role")).isEqualTo("MEMBER");
        assertThat((String) read(result, "$.workspaceId")).isEqualTo(workspaceA.toString());
        assertThat(body(result)).doesNotContain("password");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, email, role, workspace_id, updated_at > created_at AS touched FROM users WHERE id = ?",
                lauraId);
        assertThat(row.get("name")).isEqualTo("Laura García");
        assertThat(row.get("email")).isEqualTo(email("laura"));
        assertThat(row.get("role")).isEqualTo("MEMBER");
        assertThat(row.get("workspace_id")).isEqualTo(workspaceA);
        assertThat(row.get("touched")).isEqualTo(true);
        // The password is unchanged: the old one still logs in, the one in the request does not.
        assertThat(status(send(null, post("/api/auth/login"), loginBody(email("laura"), PASSWORD)))).isEqualTo(200);
        assertThat(status(send(null, post("/api/auth/login"), loginBody(email("laura"), "another-Pa55word"))))
                .isEqualTo(401);
        // Her history now shows the new name.
        List<String> authors = read(ok(anaToken, get("/api/tickets/{t}/comments", anaTicket), null),
                "$[*].authorName");
        assertThat(authors).containsExactly("Laura García");
    }

    @Test
    void anInvalidNameIsRejectedAndChangesNothing() throws Exception {
        for (String body : List.of("{\"name\": \"   \"}", "{}", "{\"name\": \"" + "x".repeat(256) + "\"}")) {
            MvcResult result = send(anaToken, patch("/api/workspaces/{w}/members/{u}", workspaceA, lauraId), body);
            assertThat(status(result)).as(body).isEqualTo(400);
            assertThat(message(result)).as(body).startsWith("name must");
        }
        assertThat(nameOf(lauraId)).isEqualTo("Laura");
    }

    @Test
    void aMemberCannotEditAnyoneAndTheAttemptChangesNothing() throws Exception {
        for (UUID target : List.of(pedroId, anaId, lauraId)) {
            MvcResult refused = send(lauraToken, patch("/api/workspaces/{w}/members/{u}", workspaceA, target), """
                    {"name": "Hacked"}
                    """);
            assertForbidden(refused, "Only workspace admins can edit members");
        }
        assertThat(nameOf(pedroId)).isEqualTo("Pedro");
        assertThat(nameOf(anaId)).isEqualTo("ana");
        assertThat(nameOf(lauraId)).isEqualTo("Laura");
    }

    // ------------------------------------------------------------------
    // remove: who may, and who can be removed
    // ------------------------------------------------------------------

    @Test
    void aMemberCannotRemoveAnyone() throws Exception {
        for (UUID target : List.of(pedroId, anaId, lauraId)) {
            assertForbidden(send(lauraToken, delete("/api/workspaces/{w}/members/{u}", workspaceA, target), null),
                    "Only workspace admins can remove members");
        }
        assertThat(memberNames(anaToken)).containsExactly("ana", "Laura", "Pedro");
        assertThat(status(send(lauraToken, get("/api/auth/me"), null))).isEqualTo(200);
    }

    @Test
    void anAdminCannotRemoveThemselves() throws Exception {
        MvcResult refused = send(anaToken, delete("/api/workspaces/{w}/members/{u}", workspaceA, anaId), null);

        assertThat(status(refused)).isEqualTo(400);
        assertThat(message(refused)).isEqualTo("You cannot remove yourself from the workspace");
        assertThat(status(send(anaToken, get("/api/auth/me"), null))).isEqualTo(200);
        assertThat(memberNames(anaToken)).containsExactly("ana", "Laura", "Pedro");
    }

    /**
     * Another workspace's member is exactly as absent as a random id - for
     * editing and removing, through the other workspace's path or one's own.
     */
    @Test
    void anotherWorkspacesMemberIsNotFound() throws Exception {
        UUID random = UUID.randomUUID();
        MockHttpServletRequestBuilder[] throughOwnPath = {
            patch("/api/workspaces/{w}/members/{u}", workspaceB, lauraId),
            delete("/api/workspaces/{w}/members/{u}", workspaceB, lauraId)};
        MockHttpServletRequestBuilder[] randomThroughOwnPath = {
            patch("/api/workspaces/{w}/members/{u}", workspaceB, random),
            delete("/api/workspaces/{w}/members/{u}", workspaceB, random)};
        for (int i = 0; i < throughOwnPath.length; i++) {
            MvcResult foreign = send(brunoToken, throughOwnPath[i], "{\"name\": \"Hacked\"}");
            MvcResult absent = send(brunoToken, randomThroughOwnPath[i], "{\"name\": \"Hacked\"}");
            assertThat(status(foreign)).isEqualTo(404);
            assertThat(message(foreign).replace(lauraId.toString(), "<id>"))
                    .isEqualTo(message(absent).replace(random.toString(), "<id>"));
        }

        MvcResult viaTheirPath = send(brunoToken, delete("/api/workspaces/{w}/members/{u}", workspaceA, lauraId),
                null);
        assertThat(status(viaTheirPath)).isEqualTo(404);
        assertThat(message(viaTheirPath)).isEqualTo("Workspace not found: " + workspaceA);
        assertThat(status(send(brunoToken, patch("/api/workspaces/{w}/members/{u}", workspaceA, lauraId),
                "{\"name\": \"Hacked\"}"))).isEqualTo(404);

        assertThat(nameOf(lauraId)).isEqualTo("Laura");
        assertThat(status(send(lauraToken, get("/api/auth/me"), null))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // remove: what it does
    // ------------------------------------------------------------------

    @Test
    void removingAMemberKeepsTheirHistoryAndUnassignsTheirTickets() throws Exception {
        MvcResult removed = send(anaToken, delete("/api/workspaces/{w}/members/{u}", workspaceA, lauraId), null);
        assertThat(status(removed)).as(body(removed)).isEqualTo(204);
        assertThat(body(removed)).isEmpty();

        // No longer a member: not listed, not found, not assignable, not removable or editable again.
        assertThat(memberNames(anaToken)).containsExactly("ana", "Pedro");
        assertThat(status(send(anaToken, get("/api/users/{u}", lauraId), null))).isEqualTo(404);
        assertThat(status(send(anaToken, patch("/api/tickets/{t}", anaTicket), """
                {"assigneeId": "%s"}
                """.formatted(lauraId)))).isEqualTo(404);
        assertThat(status(send(anaToken, delete("/api/workspaces/{w}/members/{u}", workspaceA, lauraId), null)))
                .isEqualTo(404);
        assertThat(status(send(anaToken, patch("/api/workspaces/{w}/members/{u}", workspaceA, lauraId),
                "{\"name\": \"Laura\"}"))).isEqualTo(404);

        // Her tickets are unassigned; the one she created is still hers and otherwise unchanged.
        MvcResult hers = ok(anaToken, get("/api/tickets/{t}", lauraTicket), null);
        assertThat((Object) read(hers, "$.assigneeId")).isNull();
        assertThat((String) read(hers, "$.creatorId")).isEqualTo(lauraId.toString());
        assertThat((String) read(hers, "$.title")).isEqualTo("Laura's ticket");
        MvcResult anas = ok(anaToken, get("/api/tickets/{t}", anaTicket), null);
        assertThat((Object) read(anas, "$.assigneeId")).isNull();
        assertThat((String) read(anas, "$.status")).isEqualTo("IN_PROGRESS");

        // Her comment stays, under her name; the history keeps what she did and says who unassigned her.
        MvcResult comments = ok(anaToken, get("/api/tickets/{t}/comments", anaTicket), null);
        assertThat((List<String>) read(comments, "$[*].id")).containsExactly(lauraComment.toString());
        assertThat((List<String>) read(comments, "$[*].authorName")).containsExactly("Laura");
        MvcResult activities = ok(anaToken, get("/api/tickets/{t}/activities", anaTicket), null);
        assertThat((List<String>) read(activities, "$[?(@.type == 'STATUS_CHANGED')].userName"))
                .containsExactly("Laura");
        List<Map<String, Object>> unassigned = read(activities,
                "$[?(@.type == 'ASSIGNEE_CHANGED' && @.newValue == null)]");
        assertThat(unassigned).singleElement().satisfies(activity -> {
            assertThat(activity.get("oldValue")).isEqualTo(lauraId.toString());
            assertThat(activity.get("userId")).isEqualTo(anaId.toString());
        });
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM activities WHERE ticket_id = ? "
                + "AND type = 'ASSIGNEE_CHANGED' AND new_value IS NULL", Integer.class, lauraTicket)).isEqualTo(1);

        // Pedro is untouched and still works normally.
        assertThat(status(send(pedroToken, get("/api/tickets/{t}", anaTicket), null))).isEqualTo(200);
    }

    @Test
    void aRemovedMemberCanNoLongerLogIn() throws Exception {
        ok(anaToken, delete("/api/workspaces/{w}/members/{u}", workspaceA, lauraId), null);

        MvcResult withTheirPassword = send(null, post("/api/auth/login"), loginBody(email("laura"), PASSWORD));
        MvcResult unknown = send(null, post("/api/auth/login"), loginBody(email("nobody"), PASSWORD));

        assertThat(status(withTheirPassword)).isEqualTo(401);
        assertThat(message(withTheirPassword)).isEqualTo("Invalid email or password");
        // Exactly like an address that was never registered: nothing says the account existed.
        assertThat(body(withTheirPassword).replaceAll("\"timestamp\":\"[^\"]*\"", ""))
                .isEqualTo(body(unknown).replaceAll("\"timestamp\":\"[^\"]*\"", ""));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email, password_hash, removed_at FROM users WHERE id = ?", lauraId);
        assertThat(row.get("email")).isEqualTo("removed-" + lauraId + "@removed.invalid");
        assertThat(row.get("password_hash")).isEqualTo("{removed}");
        assertThat(row.get("removed_at")).isNotNull();
    }

    @Test
    void aTokenIssuedBeforeTheRemovalNoLongerOpensTheApi() throws Exception {
        assertThat(status(send(lauraToken, get("/api/auth/me"), null))).isEqualTo(200);

        ok(anaToken, delete("/api/workspaces/{w}/members/{u}", workspaceA, lauraId), null);

        for (MockHttpServletRequestBuilder request : List.of(get("/api/auth/me"),
                get("/api/workspaces/{w}/members", workspaceA), get("/api/tickets/{t}", anaTicket))) {
            MvcResult result = send(lauraToken, request, null);
            assertThat(status(result)).as(result.getRequest().getRequestURI()).isEqualTo(401);
            assertThat(message(result)).isEqualTo("Invalid or expired token");
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .isEqualTo("Bearer error=\"invalid_token\"");
        }
        MvcResult write = send(lauraToken, post("/api/comments"), """
                {"ticketId": "%s", "content": "still here?"}
                """.formatted(anaTicket));
        assertThat(status(write)).isEqualTo(401);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM comments WHERE ticket_id = ?", Integer.class,
                anaTicket)).isEqualTo(1);
    }

    @Test
    void theRemovedMembersEmailCanBeGivenToANewAccount() throws Exception {
        ok(anaToken, delete("/api/workspaces/{w}/members/{u}", workspaceA, lauraId), null);

        UUID newLaura = addMember("Laura", email("laura"));

        assertThat(newLaura).isNotEqualTo(lauraId);
        assertThat(status(send(null, post("/api/auth/login"), loginBody(email("laura"), PASSWORD)))).isEqualTo(200);
        // The old tickets still belong to the old account, not the new one.
        assertThat((String) read(ok(anaToken, get("/api/tickets/{t}", lauraTicket), null), "$.creatorId"))
                .isEqualTo(lauraId.toString());
    }

    // ------------------------------------------------------------------

    private MvcResult register(String name, String workspace) throws Exception {
        return ok(null, post("/api/auth/register"), """
                {"name": "%s", "email": "%s", "password": "%s", "workspaceName": "%s %s"}
                """.formatted(name, email(name), PASSWORD, tag, workspace));
    }

    private UUID addMember(String name, String email) throws Exception {
        return id(ok(anaToken, post("/api/workspaces/{w}/members", workspaceA), """
                {"name": "%s", "email": "%s", "password": "%s"}
                """.formatted(name, email, PASSWORD)));
    }

    private String login(String email, String password) throws Exception {
        return read(ok(null, post("/api/auth/login"), loginBody(email, password)), "$.accessToken");
    }

    private static String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private List<String> memberNames(String token) throws Exception {
        return read(ok(token, get("/api/workspaces/{w}/members", workspaceA), null), "$[*].name");
    }

    private String nameOf(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT name FROM users WHERE id = ?", String.class, userId);
    }

    private String email(String name) {
        return tag + "-" + name + "@example.com";
    }

    private static void assertForbidden(MvcResult result, String message) throws Exception {
        assertThat(status(result)).as(body(result)).isEqualTo(403);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat((String) read(result, "$.error")).isEqualTo("Forbidden");
        assertThat(message(result)).isEqualTo(message);
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
