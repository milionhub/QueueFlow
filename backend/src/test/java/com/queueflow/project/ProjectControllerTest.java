package com.queueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.project.dto.UpdateProjectRequest;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.security.WithAuthenticatedUser;
import com.queueflow.user.UserRole;

/**
 * Web-layer slice with ProjectService mocked, same approach as
 * WorkspaceControllerTest: real MVC mapping, bean validation, JSON
 * serialization and the real security filter chain, run as an
 * authenticated user (@WithAuthenticatedUser). No request sends a CSRF token.
 */
@WebMvcTest({ProjectController.class, WorkspaceProjectController.class})
@Import(WebSecurityTestConfiguration.class)
@WithAuthenticatedUser
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** The principal @WithAuthenticatedUser installs: the only possible acting user. */
    private static final AuthenticatedUser ACTOR = new AuthenticatedUser(
            UUID.fromString(WithAuthenticatedUser.USER_ID), UUID.fromString(WithAuthenticatedUser.WORKSPACE_ID),
            UserRole.ADMIN);

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

        verify(projectService, never()).create(any(), any());
    }

    // ---------------------------------------------------------------
    // POST
    // ---------------------------------------------------------------

    @Test
    void postValidRequestReturns201WithBodyAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(projectService.create(eq(ACTOR), any())).thenReturn(projectResponse(id, workspaceId));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "QueueFlow Backend", "key": "  back  ",
                                 "description": "Backend development"}
                                """))
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
        when(projectService.create(eq(ACTOR), any())).thenReturn(projectResponse(UUID.randomUUID(), workspaceId));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "QueueFlow Backend", "key": "  back  ",
                                 "description": "Backend development"}
                                """))
                .andExpect(status().isCreated());

        // Key arrives un-normalized: normalization is ProjectService's job.
        verify(projectService).create(ACTOR, 
                new CreateProjectRequest("QueueFlow Backend", "  back  ", "Backend development"));
    }

    @Test
    void postBlankNameIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"name": "   ", "key": "BACK"}
                """);
    }

    @Test
    void postBlankKeyIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"name": "QueueFlow Backend", "key": "   "}
                """);
    }

    /**
     * There is no workspace input: projects are created in the caller's
     * workspace. A leftover workspaceId in the body is ignored like any
     * unknown JSON property and cannot reach the service.
     */
    @Test
    void postCreatesInTheCallersWorkspaceWhateverWorkspaceIdIsSent() throws Exception {
        when(projectService.create(eq(ACTOR), any())).thenReturn(projectResponse(UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "QueueFlow Backend", "key": "BACK"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        verify(projectService).create(ACTOR, new CreateProjectRequest("QueueFlow Backend", "BACK", null));
    }

    @Test
    void postOversizedKeyIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"name": "QueueFlow Backend", "key": "ABCDEFGHIJK"}
                """);
    }

    // ---------------------------------------------------------------
    // GET BY ID
    // ---------------------------------------------------------------

    @Test
    void getByIdReturns200WithExpectedJsonAndDelegatesExactUuid() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(projectService.getById(ACTOR, id)).thenReturn(projectResponse(id, workspaceId));

        mockMvc.perform(get("/api/projects/{projectId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("QueueFlow Backend"))
                .andExpect(jsonPath("$.key").value("BACK"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                // Internal ticket-allocation counter: never part of the API.
                .andExpect(jsonPath("$.nextTicketNumber").doesNotExist())
                .andExpect(jsonPath("$.workspace").doesNotExist());

        verify(projectService).getById(ACTOR, id);
    }

    // ---------------------------------------------------------------
    // GET BY KEY
    // ---------------------------------------------------------------

    @Test
    void getByKeyReturns200AndDelegatesTheRawKeyForTheCallersWorkspace() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        // Lowercase with surrounding spaces: if the controller trimmed or
        // upper-cased it, this stub would not match and verify would fail.
        String rawKey = "  back ";
        when(projectService.getByKey(ACTOR, rawKey)).thenReturn(projectResponse(id, workspaceId));

        mockMvc.perform(get("/api/projects/by-key")
                        .param("key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.key").value("BACK"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.workspace").doesNotExist());

        // Also proves "/by-key" is routed to the literal mapping, never
        // captured by "/{projectId}" as a (bad) UUID path variable.
        verify(projectService).getByKey(ACTOR, rawKey);
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
        when(projectService.getByWorkspace(ACTOR, workspaceId)).thenReturn(List.of(
                projectResponse(first, workspaceId), projectResponse(second, workspaceId)));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.toString()))
                .andExpect(jsonPath("$[1].id").value(second.toString()))
                .andExpect(jsonPath("$[0].key").value("BACK"))
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$[*].workspace").doesNotExist());

        verify(projectService).getByWorkspace(ACTOR, workspaceId);
        verifyNoMoreInteractions(projectService);
    }

    @Test
    void listByWorkspaceWithNoProjectsReturns200EmptyArray() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(projectService.getByWorkspace(ACTOR, workspaceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", workspaceId))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listByWorkspaceWithNonUuidIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).getByWorkspace(any(), any());
    }

    // ---------------------------------------------------------------
    // PATCH
    // ---------------------------------------------------------------

    private UpdateProjectRequest patchAndCaptureRequest(UUID projectId, String json) throws Exception {
        when(projectService.update(eq(ACTOR), eq(projectId), any()))
                .thenReturn(projectResponse(projectId, UUID.randomUUID()));

        mockMvc.perform(patch("/api/projects/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateProjectRequest> captor = ArgumentCaptor.forClass(UpdateProjectRequest.class);
        verify(projectService).update(eq(ACTOR), eq(projectId), captor.capture());
        return captor.getValue();
    }

    @Test
    void patchReturns200WithProjectResponseAndDelegatesExactUuidAndValues() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(projectService.update(eq(ACTOR), eq(id), any())).thenReturn(projectResponse(id, workspaceId));

        mockMvc.perform(patch("/api/projects/{projectId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  QueueFlow Backend  ", "description": "Backend development"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("QueueFlow Backend"))
                .andExpect(jsonPath("$.key").value("BACK"))
                .andExpect(jsonPath("$.description").value("Backend development"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.nextTicketNumber").doesNotExist())
                .andExpect(jsonPath("$.workspace").doesNotExist());

        ArgumentCaptor<UpdateProjectRequest> captor = ArgumentCaptor.forClass(UpdateProjectRequest.class);
        verify(projectService).update(eq(ACTOR), eq(id), captor.capture());
        verifyNoMoreInteractions(projectService);
        // Raw name delegated untrimmed: trimming/validation is the service's job.
        assertThat(captor.getValue().getName()).isEqualTo("  QueueFlow Backend  ");
        assertThat(captor.getValue().descriptionPatch().isPresent()).isTrue();
        assertThat(captor.getValue().descriptionPatch().value()).isEqualTo("Backend development");
    }

    @Test
    void patchEmptyObjectLeavesNameAndDescriptionOmitted() throws Exception {
        UpdateProjectRequest request = patchAndCaptureRequest(UUID.randomUUID(), "{}");

        assertThat(request.getName()).isNull();
        assertThat(request.descriptionPatch().isPresent()).isFalse();
    }

    @Test
    void patchExplicitNullDescriptionIsPresentWithNullValue() throws Exception {
        UpdateProjectRequest request = patchAndCaptureRequest(UUID.randomUUID(), """
                {"description": null}
                """);

        assertThat(request.descriptionPatch().isPresent()).isTrue();
        assertThat(request.descriptionPatch().value()).isNull();
        assertThat(request.getName()).isNull();
    }

    @Test
    void patchWithKeyPropertyCannotChangeTheKey() throws Exception {
        // UpdateProjectRequest has no key property. Under the application's
        // current Jackson configuration an unknown property is ignored, so
        // the request reaches the service with nothing to change - there is
        // no way to express a key change.
        UpdateProjectRequest request = patchAndCaptureRequest(UUID.randomUUID(), """
                {"key": "NEWKEY"}
                """);

        assertThat(request.getName()).isNull();
        assertThat(request.descriptionPatch().isPresent()).isFalse();
    }

    @Test
    void patchWithNonUuidIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/projects/{projectId}", "ECOM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).update(any(), any(), any());
    }

    @Test
    void patchOverlongNameIsRejectedByValidationWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/projects/{projectId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).update(any(), any(), any());
    }
}
