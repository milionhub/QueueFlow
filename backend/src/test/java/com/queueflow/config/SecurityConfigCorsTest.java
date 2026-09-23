package com.queueflow.config;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.workspace.WorkspaceController;
import com.queueflow.workspace.WorkspaceService;
import com.queueflow.workspace.dto.WorkspaceResponse;

/**
 * CORS policy of the real SecurityConfig, exercised through its filter
 * chain. Preflight (OPTIONS) requests are answered by Spring Security's
 * CorsFilter itself, before any controller, so they apply to every
 * /api/** path; WorkspaceController is in the slice to also check CORS
 * headers on actual (non-preflight) requests.
 */
@WebMvcTest(WorkspaceController.class)
@Import(SecurityConfig.class)
class SecurityConfigCorsTest {

    private static final String FRONTEND = "http://localhost:5173";
    private static final String UNAPPROVED = "http://localhost:9999";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkspaceService workspaceService;

    private static WorkspaceResponse workspace(UUID id) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        return new WorkspaceResponse(id, "Acme Inc.", timestamp, timestamp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    void preflightFromFrontendIsAllowedForApiMethodsWithContentTypeAndAuthorization(String method)
            throws Exception {
        mockMvc.perform(options("/api/tickets/{ticketId}", UUID.randomUUID())
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(method)))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void actualGetFromFrontendCarriesCorsHeadersAndExposesLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(workspaceService.getById(id)).thenReturn(workspace(id));

        mockMvc.perform(get("/api/workspaces/{workspaceId}", id).header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Location")))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void actualPostFromFrontendReturnsLocationAndExposesItToTheBrowser() throws Exception {
        UUID id = UUID.randomUUID();
        when(workspaceService.create(any())).thenReturn(workspace(id));

        mockMvc.perform(post("/api/workspaces")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acme Inc."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/workspaces/" + id)))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Location")));
    }

    @Test
    void preflightRequestingAHeaderOutsideTheAllowListIsRejected() throws Exception {
        // Allowed headers are an explicit list, not "*".
        mockMvc.perform(options("/api/workspaces")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Custom-Header"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightFromUnapprovedOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/workspaces")
                        .header(HttpHeaders.ORIGIN, UNAPPROVED)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    void actualRequestFromUnapprovedOriginGetsNoCorsPermissionAndNeverReachesTheController() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}", UUID.randomUUID()).header(HttpHeaders.ORIGIN, UNAPPROVED))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verifyNoInteractions(workspaceService);
    }
}
