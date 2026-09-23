package com.queueflow.workspace;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.security.AuthenticatedUser;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.security.WithAuthenticatedUser;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.dto.WorkspaceResponse;

/**
 * Web-layer slice: real Spring MVC request mapping, bean validation, JSON
 * (de)serialization and the real security filter chain, with the service
 * mocked, run as an authenticated user (@WithAuthenticatedUser). Workspaces
 * are created by registration (see AuthControllerTest), not by this
 * controller.
 */
@WebMvcTest(WorkspaceController.class)
@Import(WebSecurityTestConfiguration.class)
@WithAuthenticatedUser
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** The principal @WithAuthenticatedUser installs: the only possible acting user. */
    private static final AuthenticatedUser ACTOR = new AuthenticatedUser(
            UUID.fromString(WithAuthenticatedUser.USER_ID), UUID.fromString(WithAuthenticatedUser.WORKSPACE_ID),
            UserRole.ADMIN);

    @MockitoBean
    private WorkspaceService workspaceService;

    private static WorkspaceResponse workspaceResponse(UUID id, String name) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        return new WorkspaceResponse(id, name, timestamp, timestamp);
    }

    @Test
    void getExistingWorkspaceReturns200WithExpectedJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(workspaceService.getById(ACTOR, id)).thenReturn(workspaceResponse(id, "Acme Inc."));

        mockMvc.perform(get("/api/workspaces/{workspaceId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Acme Inc."))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        verify(workspaceService).getById(ACTOR, id);
    }

    /** Replaced by POST /api/auth/register: a workspace never exists without its first user. */
    @Test
    void workspacesCanNoLongerBeCreatedDirectly() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acme Inc."}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No endpoint matches this request"));

        verifyNoInteractions(workspaceService);
    }

    /** Only the API, health and docs are served: a token does not open anything else. */
    @Test
    void nonApiPathsAreDeniedEvenWithAuthentication() throws Exception {
        mockMvc.perform(get("/not-an-api-path"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/not-an-api-path"));
    }

    @Test
    @WithAnonymousUser
    void workspaceReadsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(workspaceService);
    }
}
