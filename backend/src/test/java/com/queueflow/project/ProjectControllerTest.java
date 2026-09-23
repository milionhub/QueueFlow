package com.queueflow.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.config.SecurityConfig;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;

/**
 * Web-layer slice with ProjectService mocked, same approach as
 * WorkspaceControllerTest: real MVC mapping, bean validation, JSON
 * serialization and the real (temporary) SecurityConfig. No request sends
 * credentials or a CSRF token.
 */
@WebMvcTest({ProjectController.class, WorkspaceProjectController.class})
@Import(SecurityConfig.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    private static ProjectResponse projectResponse(UUID id, UUID workspaceId) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        return new ProjectResponse(id, "QueueFlow Backend", "BACK", "Backend development", workspaceId,
                timestamp, timestamp);
    }

    private void postExpectingBadRequest(String json) throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).create(any());
    }

    // ---------------------------------------------------------------
    // POST
    // ---------------------------------------------------------------

    @Test
    void postValidRequestReturns201WithBodyAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(projectService.create(any())).thenReturn(projectResponse(id, workspaceId));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "QueueFlow Backend", "key": "  back  ",
                                 "description": "Backend development"}
                                """.formatted(workspaceId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/projects/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("QueueFlow Backend"))
                .andExpect(jsonPath("$.key").value("BACK"))
                .andExpect(jsonPath("$.description").value("Backend development"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                // Internal ticket-allocation counter: never part of the API.
                .andExpect(jsonPath("$.nextTicketNumber").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                // Only the workspace id is exposed, never the Workspace entity.
                .andExpect(jsonPath("$.workspace").doesNotExist());
    }

    @Test
    void postDelegatesExactDeserializedRequestWithRawKeyToService() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(projectService.create(any())).thenReturn(projectResponse(UUID.randomUUID(), workspaceId));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "QueueFlow Backend", "key": "  back  ",
                                 "description": "Backend development"}
                                """.formatted(workspaceId)))
                .andExpect(status().isCreated());

        // Key arrives un-normalized: normalization is ProjectService's job.
        verify(projectService).create(
                new CreateProjectRequest(workspaceId, "QueueFlow Backend", "  back  ", "Backend development"));
    }

    @Test
    void postBlankNameIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"workspaceId": "%s", "name": "   ", "key": "BACK"}
                """.formatted(UUID.randomUUID()));
    }

    @Test
    void postBlankKeyIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"workspaceId": "%s", "name": "QueueFlow Backend", "key": "   "}
                """.formatted(UUID.randomUUID()));
    }

    @Test
    void postMissingWorkspaceIdIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"name": "QueueFlow Backend", "key": "BACK"}
                """);
    }

    @Test
    void postOversizedKeyIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"workspaceId": "%s", "name": "QueueFlow Backend", "key": "ABCDEFGHIJK"}
                """.formatted(UUID.randomUUID()));
    }

    // ---------------------------------------------------------------
    // GET BY ID
    // ---------------------------------------------------------------

    @Test
    void getByIdReturns200WithExpectedJsonAndDelegatesExactUuid() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(projectService.getById(id)).thenReturn(projectResponse(id, workspaceId));

        mockMvc.perform(get("/api/projects/{projectId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("QueueFlow Backend"))
                .andExpect(jsonPath("$.key").value("BACK"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                // Internal ticket-allocation counter: never part of the API.
                .andExpect(jsonPath("$.nextTicketNumber").doesNotExist())
                .andExpect(jsonPath("$.workspace").doesNotExist());

        verify(projectService).getById(id);
    }

    // ---------------------------------------------------------------
    // GET BY KEY
    // ---------------------------------------------------------------

    @Test
    void getByKeyReturns200AndDelegatesExactWorkspaceIdAndRawKey() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        // Lowercase with surrounding spaces: if the controller trimmed or
        // upper-cased it, this stub would not match and verify would fail.
        String rawKey = "  back ";
        when(projectService.getByWorkspaceAndKey(workspaceId, rawKey)).thenReturn(projectResponse(id, workspaceId));

        mockMvc.perform(get("/api/projects/by-key")
                        .param("workspaceId", workspaceId.toString())
                        .param("key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.key").value("BACK"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.workspace").doesNotExist());

        // Also proves "/by-key" is routed to the literal mapping, never
        // captured by "/{projectId}" as a (bad) UUID path variable.
        verify(projectService).getByWorkspaceAndKey(workspaceId, rawKey);
        verifyNoMoreInteractions(projectService);
    }

    // ---------------------------------------------------------------
    // LIST BY WORKSPACE (WorkspaceProjectController)
    // ---------------------------------------------------------------

    @Test
    void listByWorkspaceReturns200ArrayInServiceOrderAndDelegatesExactWorkspaceId() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        // Deliberately not name-sorted: the controller must not re-sort.
        when(projectService.getByWorkspace(workspaceId)).thenReturn(List.of(
                projectResponse(first, workspaceId), projectResponse(second, workspaceId)));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.toString()))
                .andExpect(jsonPath("$[1].id").value(second.toString()))
                .andExpect(jsonPath("$[0].key").value("BACK"))
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$[*].workspace").doesNotExist());

        verify(projectService).getByWorkspace(workspaceId);
        verifyNoMoreInteractions(projectService);
    }

    @Test
    void listByWorkspaceWithNoProjectsReturns200EmptyArray() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(projectService.getByWorkspace(workspaceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", workspaceId))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listByWorkspaceWithNonUuidIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).getByWorkspace(any());
    }
}
