package com.queueflow.dashboard;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.dashboard.dto.DashboardAssignedResponse;
import com.queueflow.dashboard.dto.DashboardProjectResponse;
import com.queueflow.dashboard.dto.DashboardResponse;
import com.queueflow.dashboard.dto.DashboardStatusCountResponse;
import com.queueflow.dashboard.dto.DashboardTicketResponse;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.security.WithAuthenticatedUser;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.user.UserRole;

/**
 * Web-layer slice: request mapping, the JSON shape of the dashboard and
 * the standard error bodies, with the service mocked. The rules
 * themselves are covered against PostgreSQL by
 * DashboardServiceIntegrationTest.
 */
@WebMvcTest(DashboardController.class)
@Import(WebSecurityTestConfiguration.class)
@WithAuthenticatedUser
class DashboardControllerTest {

    private static final UUID WORKSPACE_ID = UUID.fromString(WithAuthenticatedUser.WORKSPACE_ID);

    /** The principal @WithAuthenticatedUser installs: the only possible acting user. */
    private static final AuthenticatedUser ACTOR = new AuthenticatedUser(
            UUID.fromString(WithAuthenticatedUser.USER_ID), WORKSPACE_ID, UserRole.ADMIN);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void returnsTheDashboardWithNullableAssigneeFieldsPresent() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-09-24T10:15:30Z");
        DashboardTicketResponse assigned = new DashboardTicketResponse(ticketId, projectId, "CORE-7", "Fix login",
                TicketStatus.IN_PROGRESS, TicketPriority.HIGH, assigneeId, "Ana", updatedAt);
        DashboardTicketResponse unassigned = new DashboardTicketResponse(UUID.randomUUID(), projectId, "CORE-8",
                "Write docs", TicketStatus.DONE, TicketPriority.LOW, null, null, updatedAt);
        List<DashboardStatusCountResponse> statusCounts = Arrays.stream(TicketStatus.values())
                .map(status -> new DashboardStatusCountResponse(status, status.ordinal()))
                .toList();
        when(dashboardService.getByWorkspace(ACTOR, WORKSPACE_ID)).thenReturn(new DashboardResponse(
                statusCounts, 3,
                List.of(new DashboardProjectResponse(projectId, "CORE", "Core platform", 9, 21)),
                new DashboardAssignedResponse(14, List.of(assigned)),
                List.of(assigned, unassigned)));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/dashboard", WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCounts.length()").value(5))
                .andExpect(jsonPath("$.statusCounts[0].status").value("BACKLOG"))
                .andExpect(jsonPath("$.statusCounts[0].count").value(0))
                .andExpect(jsonPath("$.statusCounts[4].status").value("DONE"))
                .andExpect(jsonPath("$.statusCounts[4].count").value(4))
                .andExpect(jsonPath("$.unassignedOpenCount").value(3))
                .andExpect(jsonPath("$.projects[0].id").value(projectId.toString()))
                .andExpect(jsonPath("$.projects[0].key").value("CORE"))
                .andExpect(jsonPath("$.projects[0].name").value("Core platform"))
                .andExpect(jsonPath("$.projects[0].openTicketCount").value(9))
                .andExpect(jsonPath("$.projects[0].ticketCount").value(21))
                .andExpect(jsonPath("$.assignedToMe.openCount").value(14))
                .andExpect(jsonPath("$.assignedToMe.tickets[0].id").value(ticketId.toString()))
                .andExpect(jsonPath("$.recentlyUpdated[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.recentlyUpdated[0].displayKey").value("CORE-7"))
                .andExpect(jsonPath("$.recentlyUpdated[0].title").value("Fix login"))
                .andExpect(jsonPath("$.recentlyUpdated[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.recentlyUpdated[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.recentlyUpdated[0].assigneeId").value(assigneeId.toString()))
                .andExpect(jsonPath("$.recentlyUpdated[0].assigneeName").value("Ana"))
                .andExpect(jsonPath("$.recentlyUpdated[0].updatedAt").value("2026-09-24T10:15:30Z"))
                // Unassigned: the keys are present, with null values.
                .andExpect(jsonPath("$.recentlyUpdated[1].assigneeId").isEmpty())
                .andExpect(jsonPath("$.recentlyUpdated[1].assigneeName").isEmpty())
                .andExpect(jsonPath("$.recentlyUpdated[1].description").doesNotExist())
                .andExpect(jsonPath("$.recentlyUpdated[1].labels").doesNotExist());

        verify(dashboardService).getByWorkspace(ACTOR, WORKSPACE_ID);
    }

    @Test
    @WithAuthenticatedUser(role = UserRole.MEMBER)
    void membersReadTheDashboardToo() throws Exception {
        AuthenticatedUser member = new AuthenticatedUser(UUID.fromString(WithAuthenticatedUser.USER_ID),
                WORKSPACE_ID, UserRole.MEMBER);
        when(dashboardService.getByWorkspace(member, WORKSPACE_ID)).thenReturn(new DashboardResponse(
                List.of(), 0, List.of(), new DashboardAssignedResponse(0, List.of()), List.of()));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/dashboard", WORKSPACE_ID))
                .andExpect(status().isOk());

        verify(dashboardService).getByWorkspace(member, WORKSPACE_ID);
    }

    @Test
    void anotherWorkspaceIsAnsweredWithTheStandardNotFoundBody() throws Exception {
        UUID foreignId = UUID.randomUUID();
        when(dashboardService.getByWorkspace(ACTOR, foreignId))
                .thenThrow(new ResourceNotFoundException("Workspace not found: " + foreignId));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/dashboard", foreignId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Workspace not found: " + foreignId))
                .andExpect(jsonPath("$.path").value("/api/workspaces/" + foreignId + "/dashboard"));
    }

    @Test
    void malformedWorkspaceIdIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/dashboard", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dashboardService);
    }

    @Test
    void theDashboardIsReadOnly() throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/dashboard", WORKSPACE_ID))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(dashboardService);
    }

    @Test
    @WithAnonymousUser
    void theDashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/dashboard", WORKSPACE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(dashboardService);
    }
}
