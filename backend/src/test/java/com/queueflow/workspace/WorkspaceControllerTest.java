package com.queueflow.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.queueflow.workspace.dto.CreateWorkspaceRequest;
import com.queueflow.workspace.dto.WorkspaceResponse;

/**
 * Web-layer slice: real Spring MVC request mapping, bean validation, JSON
 * (de)serialization and the real (temporary) SecurityConfig, with the
 * service mocked. No request here sends a CSRF token or credentials, so a
 * passing POST also proves the temporary Phase 1 security policy.
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
    void postValidRequestReturns201WithBodyAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(workspaceService.create(any())).thenReturn(workspaceResponse(id, "Acme Inc."));

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acme Inc."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/workspaces/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Acme Inc."))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void postDelegatesDeserializedRequestToService() throws Exception {
        when(workspaceService.create(any())).thenReturn(workspaceResponse(UUID.randomUUID(), "Acme Inc."));

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acme Inc."}
                                """))
                .andExpect(status().isCreated());

        verify(workspaceService).create(new CreateWorkspaceRequest("Acme Inc."));
    }

    @Test
    void postBlankNameIsRejectedByValidationWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   "}
                                """))
                .andExpect(status().isBadRequest());

        verify(workspaceService, never()).create(any());
    }

    @Test
    void postOverlongNameIsRejectedByValidationWithoutCallingService() throws Exception {
        String overlongName = "a".repeat(256);

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + overlongName + "\"}"))
                .andExpect(status().isBadRequest());

        verify(workspaceService, never()).create(any());
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

    @Test
    void nonApiPathsStillRequireAuthentication() throws Exception {
        // Guards the temporary policy's boundary: only /api/** and
        // /actuator/health are opened, security is not disabled wholesale.
        mockMvc.perform(get("/not-an-api-path"))
                .andExpect(status().isForbidden());
    }
}
