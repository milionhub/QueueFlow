package com.queueflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.config.SecurityConfig;
import com.queueflow.label.LabelController;
import com.queueflow.label.LabelService;
import com.queueflow.project.ProjectController;
import com.queueflow.project.ProjectService;

import jakarta.servlet.ServletException;

/**
 * Exercises GlobalExceptionHandler through the real Spring MVC
 * exception-resolution path: real controllers, real SecurityConfig and the
 * advice (auto-included by the web slice), with the services mocked to
 * throw the actual service-layer exceptions.
 */
@WebMvcTest({ProjectController.class, LabelController.class})
@Import(SecurityConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private LabelService labelService;

    /** Structure shared by every ApiErrorResponse, and nothing internal leaks. */
    private static void expectApiError(ResultActions result, int status, String error, String message, String path)
            throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
        String body = result.andReturn().getResponse().getContentAsString();
        // No exception class names, package names or stack frames.
        assertThat(body).doesNotContain("Exception", "com.queueflow");
    }

    // ---------------------------------------------------------------
    // 404 / 409
    // ---------------------------------------------------------------

    @Test
    void resourceNotFoundBecomes404WithStandardBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectService.getById(id)).thenThrow(new ResourceNotFoundException("Project not found: " + id));

        expectApiError(mockMvc.perform(get("/api/projects/{projectId}", id)),
                404, "Not Found", "Project not found: " + id, "/api/projects/" + id);
    }

    @Test
    void resourceAlreadyExistsBecomes409WithStandardBody() throws Exception {
        when(projectService.create(any()))
                .thenThrow(new ResourceAlreadyExistsException("Project key already exists in workspace: QF"));

        expectApiError(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "QueueFlow", "key": "qf"}
                                """.formatted(UUID.randomUUID()))),
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
                                """.formatted(UUID.randomUUID()))),
                409, "Conflict", "Label already exists in workspace: Bug", "/api/labels");
    }

    // ---------------------------------------------------------------
    // 400 - @Valid request bodies
    // ---------------------------------------------------------------

    @Test
    void singleValidationErrorBecomes400WithItsMessage() throws Exception {
        expectApiError(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "QueueFlow", "key": "QF"}
                                """)),
                400, "Bad Request", "workspaceId is required", "/api/projects");

        verify(projectService, never()).create(any());
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

        verify(projectService, never()).create(any());
    }

    // ---------------------------------------------------------------
    // timestamp / path
    // ---------------------------------------------------------------

    @Test
    void timestampIsUtcWithAtMostMicrosecondPrecision() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectService.getById(id)).thenThrow(new ResourceNotFoundException("Project not found: " + id));

        String body = mockMvc.perform(get("/api/projects/{projectId}", id))
                .andExpect(status().isNotFound())
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
    // Not owned by the 1.8A advice
    // ---------------------------------------------------------------

    @Test
    void businessRuleViolationIsDeliberatelyNotHandledYet() {
        UUID id = UUID.randomUUID();
        when(projectService.getById(id)).thenThrow(new BusinessRuleViolationException("some business rule"));

        // No @ExceptionHandler claims it, so it propagates out of MVC
        // unresolved (MockMvc rethrows it) instead of becoming a 400/404/409
        // ApiErrorResponse. Its HTTP mapping is decided once it is split.
        assertThatThrownBy(() -> mockMvc.perform(get("/api/projects/{projectId}", id)))
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(BusinessRuleViolationException.class);
    }
}
