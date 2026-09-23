package com.queueflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.jayway.jsonpath.JsonPath;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ForbiddenOperationException;
import com.queueflow.common.exception.InvalidRelationshipException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.label.LabelController;
import com.queueflow.label.LabelService;
import com.queueflow.project.ProjectController;
import com.queueflow.project.ProjectService;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.security.WithAuthenticatedUser;
import com.queueflow.ticket.ProjectTicketController;
import com.queueflow.ticket.TicketController;
import com.queueflow.ticket.TicketService;

import jakarta.servlet.ServletException;

/**
 * Exercises GlobalExceptionHandler through the real Spring MVC
 * exception-resolution path: real controllers, real SecurityConfig and the
 * advice (auto-included by the web slice), with the services mocked to
 * throw the actual service-layer exceptions. Framework cases also assert
 * which exception Spring MVC actually raised, so the mapping is tied to the
 * real Spring Framework 7 behavior rather than assumed.
 */
@WebMvcTest({ProjectController.class, LabelController.class, TicketController.class, ProjectTicketController.class})
@Import(WebSecurityTestConfiguration.class)
@WithAuthenticatedUser
class GlobalExceptionHandlerTest {

    private static final UUID ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private LabelService labelService;

    @MockitoBean
    private TicketService ticketService;

    /**
     * Everything every standardized error must satisfy: exactly the five
     * ApiErrorResponse fields with the given values, a UTC timestamp of at
     * most microsecond precision, and nothing internal in the body.
     */
    private static MvcResult expectApiError(ResultActions result, int status, String error, String message,
            String path) throws Exception {
        MvcResult mvcResult = result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
                .andReturn();
        String body = mvcResult.getResponse().getContentAsString();

        OffsetDateTime timestamp = OffsetDateTime.parse(JsonPath.read(body, "$.timestamp"));
        assertThat(timestamp.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(timestamp.getNano() % 1_000).isZero();

        // No exception class names, package or Java type names, stack frames.
        assertThat(body).doesNotContain("Exception", "com.queueflow", "java.", "jackson");
        return mvcResult;
    }

    // ---------------------------------------------------------------
    // 1.8A: service-layer exceptions and @Valid (unchanged)
    // ---------------------------------------------------------------

    @Test
    void resourceNotFoundBecomes404WithStandardBody() throws Exception {
        when(projectService.getById(ID)).thenThrow(new ResourceNotFoundException("Project not found: " + ID));

        expectApiError(mockMvc.perform(get("/api/projects/{projectId}", ID)),
                404, "Not Found", "Project not found: " + ID, "/api/projects/" + ID);
    }

    @Test
    void resourceAlreadyExistsBecomes409WithStandardBody() throws Exception {
        when(projectService.create(any()))
                .thenThrow(new ResourceAlreadyExistsException("Project key already exists in workspace: QF"));

        expectApiError(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "QueueFlow", "key": "qf"}
                                """.formatted(ID))),
                409, "Conflict", "Project key already exists in workspace: QF", "/api/projects");
    }

    @Test
    void duplicateLabelFromAnotherControllerUsesTheSameMapping() throws Exception {
        when(labelService.create(any()))
                .thenThrow(new ResourceAlreadyExistsException("Label already exists in workspace: Bug"));

        expectApiError(mockMvc.perform(post("/api/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "Bug"}
                                """.formatted(ID))),
                409, "Conflict", "Label already exists in workspace: Bug", "/api/labels");
    }

    @Test
    void singleValidationErrorBecomes400WithItsMessage() throws Exception {
        expectApiError(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "QueueFlow", "key": "QF"}
                                """)),
                400, "Bad Request", "workspaceId is required", "/api/projects");

        verifyNoInteractions(projectService);
    }

    @Test
    void multipleValidationErrorsAreSortedAndJoinedDeterministically() throws Exception {
        // {} violates three constraints; the validator's own ordering is not
        // guaranteed, so the handler sorts the messages.
        for (int attempt = 0; attempt < 3; attempt++) {
            expectApiError(mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")),
                    400, "Bad Request",
                    "key must not be blank; name must not be blank; workspaceId is required",
                    "/api/projects");
        }

        verifyNoInteractions(projectService);
    }

    // ---------------------------------------------------------------
    // 1.8B: request errors detected by Spring MVC -> 400
    // ---------------------------------------------------------------

    static Stream<Arguments> frameworkBadRequests() {
        String ticketBody = """
                {"projectId": "%s", "title": "t", "status": "%s", "priority": "LOW", "creatorId": "%s"}
                """;
        return Stream.of(
                Arguments.of("malformed JSON",
                        post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "),
                        HttpMessageNotReadableException.class, "Malformed or missing request body", "/api/projects"),
                Arguments.of("missing request body",
                        post("/api/projects").contentType(MediaType.APPLICATION_JSON),
                        HttpMessageNotReadableException.class, "Malformed or missing request body", "/api/projects"),
                Arguments.of("unknown enum value in body",
                        post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                                .content(ticketBody.formatted(ID, "NOT_A_STATUS", ID)),
                        HttpMessageNotReadableException.class, "Malformed or missing request body", "/api/tickets"),
                Arguments.of("invalid UUID path variable",
                        get("/api/projects/{projectId}", "not-a-uuid"),
                        MethodArgumentTypeMismatchException.class, "Invalid value for parameter: projectId",
                        "/api/projects/not-a-uuid"),
                Arguments.of("invalid numeric path variable",
                        get("/api/projects/{projectId}/tickets/{ticketNumber}", ID, "not-a-number"),
                        MethodArgumentTypeMismatchException.class, "Invalid value for parameter: ticketNumber",
                        "/api/projects/" + ID + "/tickets/not-a-number"),
                Arguments.of("missing required query parameter",
                        patch("/api/tickets/{ticketId}", ID).contentType(MediaType.APPLICATION_JSON).content("{}"),
                        MissingServletRequestParameterException.class, "Missing required parameter: actorUserId",
                        "/api/tickets/" + ID));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("frameworkBadRequests")
    void frameworkRequestErrorsBecome400WithStandardBody(String description, MockHttpServletRequestBuilder request,
            Class<? extends Exception> expectedException, String message, String path) throws Exception {
        MvcResult result = expectApiError(mockMvc.perform(request), 400, "Bad Request", message, path);

        assertThat(result.getResolvedException()).isInstanceOf(expectedException);
        verifyNoInteractions(projectService, ticketService);
    }

    @Test
    void invalidQueryParameterIsReportedWithoutEchoingTheQueryString() throws Exception {
        MvcResult result = expectApiError(mockMvc.perform(get("/api/projects/by-key")
                        .param("workspaceId", "not-a-uuid")
                        .param("key", "QF")),
                400, "Bad Request", "Invalid value for parameter: workspaceId", "/api/projects/by-key");

        assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentTypeMismatchException.class);
        String path = JsonPath.read(result.getResponse().getContentAsString(), "$.path");
        assertThat(path).doesNotContain("?", "workspaceId=", "key=");
    }

    // ---------------------------------------------------------------
    // 1.8B: routing / protocol errors -> 404, 405, 415
    // ---------------------------------------------------------------

    @Test
    void unknownApiRouteBecomes404WithStandardBody() throws Exception {
        MvcResult result = expectApiError(mockMvc.perform(get("/api/this-route-does-not-exist")),
                404, "Not Found", "No endpoint matches this request", "/api/this-route-does-not-exist");

        // Spring Framework 7 raises this for any URL no controller maps.
        assertThat(result.getResolvedException()).isInstanceOf(NoResourceFoundException.class);
    }

    @Test
    void unsupportedMethodOnKnownEndpointBecomes405AndKeepsTheAllowHeader() throws Exception {
        MvcResult result = expectApiError(mockMvc.perform(delete("/api/projects/{projectId}", ID)),
                405, "Method Not Allowed", "HTTP method not allowed", "/api/projects/" + ID);

        assertThat(result.getResolvedException()).isInstanceOf(HttpRequestMethodNotSupportedException.class);
        assertThat(result.getResponse().getHeaders(HttpHeaders.ALLOW))
                .flatMap(value -> Stream.of(value.split(",")).map(String::trim).toList())
                .containsExactlyInAnyOrder("GET", "PATCH");
        verifyNoInteractions(projectService);
    }

    @Test
    void unsupportedContentTypeBecomes415AndKeepsTheAcceptHeader() throws Exception {
        MvcResult result = expectApiError(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=QueueFlow")),
                415, "Unsupported Media Type", "Unsupported Content-Type; use application/json", "/api/projects");

        assertThat(result.getResolvedException()).isInstanceOf(HttpMediaTypeNotSupportedException.class);
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCEPT)).contains("application/json");
        verifyNoInteractions(projectService);
    }

    @Test
    void timestampIsUtcWithAtMostMicrosecondPrecision() throws Exception {
        String body = mockMvc.perform(get("/api/projects/{projectId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        OffsetDateTime timestamp = OffsetDateTime.parse(JsonPath.read(body, "$.timestamp"));
        assertThat(timestamp.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(timestamp.getNano() % 1_000).isZero();
    }

    @Test
    void pathIsTheUriPathOnlyWithoutTheQueryString() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(projectService.getByWorkspaceAndKey(workspaceId, "QF"))
                .thenThrow(new ResourceNotFoundException("Project not found in workspace " + workspaceId + " with key: QF"));

        ResultActions result = mockMvc.perform(get("/api/projects/by-key")
                .param("workspaceId", workspaceId.toString())
                .param("key", "QF"));

        expectApiError(result, 404, "Not Found", "Project not found in workspace " + workspaceId + " with key: QF",
                "/api/projects/by-key");
        String path = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.path");
        assertThat(path).doesNotContain("?", "workspaceId=", "key=", "http");
    }

    // ---------------------------------------------------------------
    // 1.8C: business-rule taxonomy
    // ---------------------------------------------------------------

    @Test
    void businessRuleViolationBecomes400() throws Exception {
        when(projectService.create(any())).thenThrow(new BusinessRuleViolationException(
                "key must be 2-10 characters, using only letters A-Z and digits 0-9: A-B"));

        expectApiError(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "QueueFlow", "key": "A-B"}
                                """.formatted(ID))),
                400, "Bad Request", "key must be 2-10 characters, using only letters A-Z and digits 0-9: A-B",
                "/api/projects");
    }

    @Test
    void invalidRelationshipBecomes400WithThePathOnly() throws Exception {
        UUID actorUserId = UUID.randomUUID();
        when(ticketService.update(eq(ID), eq(actorUserId), any())).thenThrow(
                new InvalidRelationshipException("Assignee must belong to the same workspace as the project"));

        expectApiError(mockMvc.perform(patch("/api/tickets/{ticketId}", ID)
                        .param("actorUserId", actorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId": "%s"}
                                """.formatted(UUID.randomUUID()))),
                400, "Bad Request", "Assignee must belong to the same workspace as the project",
                "/api/tickets/" + ID);
    }

    @Test
    void forbiddenOperationBecomes403WithThePathOnly() throws Exception {
        UUID actorUserId = UUID.randomUUID();
        when(ticketService.update(eq(ID), eq(actorUserId), any())).thenThrow(
                new ForbiddenOperationException("Actor must belong to the same workspace as the ticket"));

        MvcResult result = expectApiError(mockMvc.perform(patch("/api/tickets/{ticketId}", ID)
                        .param("actorUserId", actorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """)),
                403, "Forbidden", "Actor must belong to the same workspace as the ticket", "/api/tickets/" + ID);

        String path = JsonPath.read(result.getResponse().getContentAsString(), "$.path");
        assertThat(path).doesNotContain("?", "actorUserId");
    }

    // ---------------------------------------------------------------
    // 1.8D: database integrity failures
    // ---------------------------------------------------------------

    /** Same shape as the real chain: Spring -> Hibernate -> JDBC driver with a SQLSTATE. */
    private static DataIntegrityViolationException integrityFailure(String sqlState) {
        return new DataIntegrityViolationException("could not execute statement [uq_labels_workspace_name]",
                new RuntimeException("hibernate ConstraintViolationException",
                        new SQLException("ERROR: duplicate key value violates unique constraint \"uq_x\"", sqlState)));
    }

    private void postProjectExpecting(DataIntegrityViolationException exception) {
        when(projectService.create(any())).thenThrow(exception);
    }

    private ResultActions postProject() throws Exception {
        return mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"workspaceId": "%s", "name": "QueueFlow", "key": "QF"}
                        """.formatted(ID)));
    }

    @Test
    void uniqueViolationRaceBecomes409WithAGenericSafeMessage() throws Exception {
        postProjectExpecting(integrityFailure("23505"));

        MvcResult result = expectApiError(postProject(),
                409, "Conflict", "Resource conflicts with existing data", "/api/projects");

        // Nothing from the database leaks: no SQL, constraint or table names, no driver text.
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("uq_", "constraint", "duplicate key", "SQL", "ERROR", "hibernate", "labels");
    }

    @Test
    void nonUniqueIntegrityFailureIsDeclinedAndStaysUnhandled() {
        // Foreign-key violation (23503): an application bug, not a client
        // conflict - the handler rethrows it, so it escapes MVC unresolved.
        postProjectExpecting(integrityFailure("23503"));

        assertThatThrownBy(this::postProject)
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void integrityFailureWithoutAnySqlStateIsDeclinedAndStaysUnhandled() {
        postProjectExpecting(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(this::postProject)
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void validRequestIsUnaffected() throws Exception {
        when(projectService.getById(ID)).thenReturn(new ProjectResponse(ID, "QueueFlow",
                "QF", null, UUID.randomUUID(), OffsetDateTime.parse("2026-09-23T10:15:30Z"),
                OffsetDateTime.parse("2026-09-23T10:15:30Z")));

        mockMvc.perform(get("/api/projects/{projectId}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("QF"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.ALLOW));
    }
}
