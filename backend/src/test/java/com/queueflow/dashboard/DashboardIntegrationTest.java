package com.queueflow.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

/**
 * The dashboard over the real stack - HTTP, real JWTs, the security chain,
 * the service and PostgreSQL - with two workspaces built through the API.
 * A: Ana (ADMIN) and Pedro (MEMBER, created by Ana and signed in with his
 * own password). B: Bruno, whose project deliberately reuses A's key
 * (CORE). No @Transactional; both workspaces are deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardIntegrationTest {

    private static final String PASSWORD = "dash-Pa55word";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String tag = "dash-" + UUID.randomUUID().toString().substring(0, 8);

    private UUID workspaceA;
    private String anaToken;
    private UUID anaId;
    private String pedroToken;
    private UUID pedroId;
    private UUID projectA;
    private UUID pedrosTicket;
    private UUID anasTicket;

    private UUID workspaceB;
    private List<String> foreignValues;

    @BeforeEach
    void createTwoWorkspaces() throws Exception {
        MvcResult ana = register("ana");
        workspaceA = UUID.fromString(read(ana, "$.user.workspaceId"));
        anaId = UUID.fromString(read(ana, "$.user.id"));
        anaToken = read(ana, "$.accessToken");
        pedroId = id(ok(anaToken, post("/api/workspaces/{w}/members", workspaceA), """
                {"name": "Pedro", "email": "%s-pedro@example.com", "password": "%s"}
                """.formatted(tag, PASSWORD)));
        pedroToken = read(ok(null, post("/api/auth/login"), """
                {"email": "%s-pedro@example.com", "password": "%s"}
                """.formatted(tag, PASSWORD)), "$.accessToken");

        projectA = id(ok(anaToken, post("/api/projects"), """
                {"name": "Core platform", "key": "CORE"}
                """));
        pedrosTicket = ticket(anaToken, projectA, "TODO", "HIGH", pedroId);
        ticket(anaToken, projectA, "DONE", "LOW", null);
        anasTicket = ticket(anaToken, projectA, "IN_PROGRESS", "CRITICAL", anaId);

        MvcResult bruno = register("bruno");
        workspaceB = UUID.fromString(read(bruno, "$.user.workspaceId"));
        String brunoToken = read(bruno, "$.accessToken");
        UUID projectB = id(ok(brunoToken, post("/api/projects"), """
                {"name": "Secret plans", "key": "CORE"}
                """));
        UUID brunoId = UUID.fromString(read(bruno, "$.user.id"));
        UUID brunosTicket = ticket(brunoToken, projectB, "BACKLOG", "CRITICAL", null);
        UUID brunosOwnTicket = ticket(brunoToken, projectB, "REVIEW", "HIGH", brunoId);
        foreignValues = List.of(workspaceB.toString(), brunoId.toString(), projectB.toString(),
                brunosTicket.toString(), brunosOwnTicket.toString(), "Secret plans", "bruno");
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
            jdbcTemplate.update("DELETE FROM tickets WHERE id IN (" + tickets + ")", w);
            jdbcTemplate.update("DELETE FROM projects WHERE workspace_id = ?", w);
            jdbcTemplate.update("DELETE FROM users WHERE workspace_id = ?", w);
            jdbcTemplate.update("DELETE FROM workspaces WHERE id = ?", w);
        }
    }

    @Test
    void withoutATokenTheDashboardIs401() throws Exception {
        MvcResult result = send(null, get("/api/workspaces/{w}/dashboard", workspaceA));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(message(result)).isEqualTo("Authentication required");
    }

    @Test
    void adminAndMemberReadTheSameWorkspaceWithTheirOwnAssignedTickets() throws Exception {
        String forAna = ok(anaToken, get("/api/workspaces/{w}/dashboard", workspaceA), null)
                .getResponse().getContentAsString();
        String forPedro = ok(pedroToken, get("/api/workspaces/{w}/dashboard", workspaceA), null)
                .getResponse().getContentAsString();

        for (String body : List.of(forAna, forPedro)) {
            assertThat(JsonPath.<List<Integer>>read(body, "$.statusCounts[*].count")).containsExactly(0, 1, 1, 0, 1);
            assertThat(JsonPath.<Integer>read(body, "$.unassignedOpenCount")).isZero();
            assertThat(JsonPath.<List<String>>read(body, "$.projects[*].id")).containsExactly(projectA.toString());
            assertThat(JsonPath.<Integer>read(body, "$.projects[0].openTicketCount")).isEqualTo(2);
            assertThat(JsonPath.<Integer>read(body, "$.projects[0].ticketCount")).isEqualTo(3);
            assertThat(JsonPath.<List<String>>read(body, "$.recentlyUpdated[*].displayKey"))
                    .containsExactlyInAnyOrder("CORE-1", "CORE-2", "CORE-3");
            assertThat(JsonPath.<Integer>read(body, "$.assignedToMe.openCount")).isEqualTo(1);
            // Workspace B shares the key CORE but contributes nothing.
            for (String foreignValue : foreignValues) {
                assertThat(body).doesNotContain(foreignValue);
            }
        }
        assertThat(JsonPath.<List<String>>read(forAna, "$.assignedToMe.tickets[*].id"))
                .containsExactly(anasTicket.toString());
        assertThat(JsonPath.<List<String>>read(forPedro, "$.assignedToMe.tickets[*].id"))
                .containsExactly(pedrosTicket.toString());
        assertThat(JsonPath.<String>read(forPedro, "$.assignedToMe.tickets[0].assigneeName")).isEqualTo("Pedro");
    }

    @Test
    void anotherWorkspaceIsNotFoundExactlyLikeAnAbsentOne() throws Exception {
        UUID randomId = UUID.randomUUID();
        for (String token : List.of(anaToken, pedroToken)) {
            MvcResult foreign = send(token, get("/api/workspaces/{w}/dashboard", workspaceB));
            MvcResult absent = send(token, get("/api/workspaces/{w}/dashboard", randomId));

            assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
            assertThat(absent.getResponse().getStatus()).isEqualTo(404);
            assertThat(message(foreign).replace(workspaceB.toString(), "<id>"))
                    .isEqualTo(message(absent).replace(randomId.toString(), "<id>"))
                    .isEqualTo("Workspace not found: <id>");
            String body = foreign.getResponse().getContentAsString();
            for (String foreignValue : foreignValues.subList(1, foreignValues.size())) {
                assertThat(body).doesNotContain(foreignValue);
            }
        }
    }

    // ------------------------------------------------------------------

    private MvcResult register(String name) throws Exception {
        return ok(null, post("/api/auth/register"), """
                {"name": "%s", "email": "%s-%s@example.com", "password": "%s", "workspaceName": "%s %s"}
                """.formatted(name, tag, name, PASSWORD, tag, name));
    }

    private UUID ticket(String token, UUID project, String status, String priority, UUID assignee)
            throws Exception {
        String assigneeJson = assignee == null ? "null" : "\"" + assignee + "\"";
        return id(ok(token, post("/api/tickets"), """
                {"projectId": "%s", "title": "%s %s ticket", "status": "%s", "priority": "%s", "assigneeId": %s}
                """.formatted(project, status, priority, status, priority, assigneeJson)));
    }

    private MvcResult send(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult ok(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult result = send(token, request);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isBetween(200, 299);
        return result;
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
