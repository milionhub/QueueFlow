package com.queueflow.workspace;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.config.SecurityConfig;
import com.queueflow.workspace.dto.WorkspaceResponse;

/**
 * Web-layer slice: real Spring MVC request mapping, bean validation, JSON
 * (de)serialization and the real (temporary) SecurityConfig, with the
 * service mocked. No request here sends credentials, so a passing GET also
 * proves the temporary Phase 1 security policy. Workspaces are created by
 * registration (see AuthControllerTest), not by this controller.
 */
@WebMvcTest(WorkspaceController.class)
@Import(SecurityConfig.class)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkspaceService workspaceService;

    private static WorkspaceResponse workspaceResponse(UUID id, String name) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        return new WorkspaceResponse(id, name, timestamp, timestamp);
    }

    @Test
    void getExistingWorkspaceReturns200WithExpectedJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(workspaceService.getById(id)).thenReturn(workspaceResponse(id, "Acme Inc."));

        mockMvc.perform(get("/api/workspaces/{workspaceId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Acme Inc."))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        verify(workspaceService).getById(id);
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

    @Test
    void nonApiPathsStillRequireAuthentication() throws Exception {
        // Guards the temporary policy's boundary: only /api/** and
        // /actuator/health are opened, security is not disabled wholesale.
        mockMvc.perform(get("/not-an-api-path"))
                .andExpect(status().isForbidden());
    }
}
