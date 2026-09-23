package com.queueflow.label;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;

import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.security.WithAuthenticatedUser;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.user.UserRole;

/**
 * Web-layer slice covering all LabelService-backed controllers
 * (LabelController, TicketLabelController and WorkspaceLabelController)
 * with LabelService mocked,
 * same approach as the other controller tests: real MVC mapping, bean
 * validation, JSON serialization and the real security filter chain, run
 * as an authenticated user (@WithAuthenticatedUser).
 * Idempotency and Activity recording are service behavior, covered by
 * LabelServiceTest / LabelServiceIntegrationTest, not here.
 */
@WebMvcTest({LabelController.class, TicketLabelController.class, WorkspaceLabelController.class})
@Import(WebSecurityTestConfiguration.class)
@WithAuthenticatedUser
class LabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** The principal @WithAuthenticatedUser installs: the only possible acting user. */
    private static final AuthenticatedUser ACTOR = new AuthenticatedUser(
            UUID.fromString(WithAuthenticatedUser.USER_ID), UUID.fromString(WithAuthenticatedUser.WORKSPACE_ID),
            UserRole.ADMIN);

    @MockitoBean
    private LabelService labelService;

    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-09-23T10:15:30Z");

    private static LabelResponse labelResponse(UUID id, String name, UUID workspaceId) {
        return new LabelResponse(id, name, workspaceId, TIMESTAMP, TIMESTAMP);
    }

    private static TicketResponse ticketResponse(UUID ticketId) {
        return new TicketResponse(ticketId, 1L, "BACK-1", "Fix checkout bug", null, TicketStatus.BACKLOG,
                TicketPriority.HIGH, UUID.randomUUID(), "BACK", UUID.randomUUID(), null, TIMESTAMP, TIMESTAMP,
                List.of());
    }

    private void postExpectingBadRequest(String json) throws Exception {
        mockMvc.perform(post("/api/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(labelService, never()).create(any(), any());
    }

    // ---------------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------------

    @Test
    void postValidRequestReturns201WithBodyAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(labelService.create(eq(ACTOR), any())).thenReturn(labelResponse(id, "Backend", workspaceId));

        mockMvc.perform(post("/api/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Backend"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/labels/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Backend"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.workspace").doesNotExist());
    }

    @Test
    void postDelegatesExactRawRequestToService() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(labelService.create(eq(ACTOR), any()))
                .thenReturn(labelResponse(UUID.randomUUID(), "Backend", workspaceId));

        mockMvc.perform(post("/api/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  BackEnd  "}
                                """))
                .andExpect(status().isCreated());

        // Name arrives untrimmed and with its original case: normalization
        // is LabelService's job.
        verify(labelService).create(ACTOR, new CreateLabelRequest("  BackEnd  "));
    }

    @Test
    void postBlankNameIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"name": "   "}
                """);
    }

    /**
     * There is no workspace input: labels are created in the caller's
     * workspace. A leftover workspaceId in the body is ignored like any
     * unknown JSON property and cannot reach the service.
     */
    @Test
    void postCreatesInTheCallersWorkspaceWhateverWorkspaceIdIsSent() throws Exception {
        when(labelService.create(eq(ACTOR), any()))
                .thenReturn(labelResponse(UUID.randomUUID(), "Bug", UUID.randomUUID()));

        mockMvc.perform(post("/api/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "Bug"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        verify(labelService).create(ACTOR, new CreateLabelRequest("Bug"));
    }

    @Test
    void postOversizedNameIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"name": "%s"}
                """.formatted("a".repeat(51)));
    }

    // ---------------------------------------------------------------
    // GET BY ID / BY NAME
    // ---------------------------------------------------------------

    @Test
    void getByIdReturns200WithExpectedJsonAndDelegatesExactUuid() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(labelService.getById(ACTOR, id)).thenReturn(labelResponse(id, "Backend", workspaceId));

        mockMvc.perform(get("/api/labels/{labelId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Backend"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.workspace").doesNotExist());

        verify(labelService).getById(ACTOR, id);
    }

    @Test
    void getByNameReturns200AndDelegatesTheRawNameForTheCallersWorkspace() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        // Mixed case with surrounding spaces: if the controller trimmed or
        // changed case, this stub would not match and verify would fail.
        String rawName = "  BackEnd ";
        when(labelService.getByName(ACTOR, rawName))
                .thenReturn(labelResponse(id, "BackEnd", workspaceId));

        mockMvc.perform(get("/api/labels/by-name")
                        .param("name", rawName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("BackEnd"));

        // Also proves "/by-name" is routed to the literal mapping, never
        // captured by "/{labelId}" as a (bad) UUID path variable.
        verify(labelService).getByName(ACTOR, rawName);
        verifyNoMoreInteractions(labelService);
    }

    // ---------------------------------------------------------------
    // TICKET-LABEL ASSOCIATIONS
    // ---------------------------------------------------------------

    @Test
    void putAddsLabelReturns200WithTicketResponseAndDelegatesExactIds() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        when(labelService.addLabelToTicket(ACTOR, ticketId, labelId)).thenReturn(ticketResponse(ticketId));

        ResultActions result = mockMvc.perform(put("/api/tickets/{ticketId}/labels/{labelId}", ticketId, labelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.displayKey").value("BACK-1"));
        expectNoTicketEntityLeakage(result);

        verify(labelService).addLabelToTicket(ACTOR, ticketId, labelId);
        verifyNoMoreInteractions(labelService);
    }

    /** actorUserId is no longer an input: a leftover query parameter cannot change who acts. */
    @Test
    void putActsAsTheAuthenticatedUserEvenIfAnObsoleteActorUserIdIsSent() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        when(labelService.addLabelToTicket(ACTOR, ticketId, labelId)).thenReturn(ticketResponse(ticketId));

        mockMvc.perform(put("/api/tickets/{ticketId}/labels/{labelId}", ticketId, labelId)
                        .param("actorUserId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        verify(labelService).addLabelToTicket(ACTOR, ticketId, labelId);
        verifyNoMoreInteractions(labelService);
    }

    @Test
    void deleteRemovesLabelReturns200WithTicketResponseAndDelegatesExactIds() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        when(labelService.removeLabelFromTicket(ACTOR, ticketId, labelId))
                .thenReturn(ticketResponse(ticketId));

        ResultActions result = mockMvc.perform(
                        delete("/api/tickets/{ticketId}/labels/{labelId}", ticketId, labelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()));
        expectNoTicketEntityLeakage(result);

        verify(labelService).removeLabelFromTicket(ACTOR, ticketId, labelId);
        verifyNoMoreInteractions(labelService);
    }

    @Test
    void deleteActsAsTheAuthenticatedUserEvenIfAnObsoleteActorUserIdIsSent() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        when(labelService.removeLabelFromTicket(ACTOR, ticketId, labelId)).thenReturn(ticketResponse(ticketId));

        mockMvc.perform(delete("/api/tickets/{ticketId}/labels/{labelId}", ticketId, labelId)
                        .param("actorUserId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        verify(labelService).removeLabelFromTicket(ACTOR, ticketId, labelId);
        verifyNoMoreInteractions(labelService);
    }

    private static void expectNoTicketEntityLeakage(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.project").doesNotExist())
                .andExpect(jsonPath("$.creator").doesNotExist())
                .andExpect(jsonPath("$.assignee").doesNotExist())
                // labels is a LabelResponse DTO array, never Label entities.
                .andExpect(jsonPath("$.labels").isArray())
                .andExpect(jsonPath("$.labels[*].workspace").doesNotExist());
    }

    // ---------------------------------------------------------------
    // LIST BY WORKSPACE (WorkspaceLabelController)
    // ---------------------------------------------------------------

    @Test
    void listByWorkspaceReturns200ArrayInServiceOrderAndDelegatesExactWorkspaceId() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID urgent = UUID.randomUUID();
        UUID bug = UUID.randomUUID();
        // Deliberately not name-sorted: the controller must not re-sort.
        when(labelService.getByWorkspace(ACTOR, workspaceId)).thenReturn(List.of(
                labelResponse(urgent, "urgent", workspaceId), labelResponse(bug, "bug", workspaceId)));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/labels", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(urgent.toString()))
                .andExpect(jsonPath("$[0].name").value("urgent"))
                .andExpect(jsonPath("$[1].id").value(bug.toString()))
                .andExpect(jsonPath("$[1].name").value("bug"))
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$[*].workspace").doesNotExist());

        verify(labelService).getByWorkspace(ACTOR, workspaceId);
        verifyNoMoreInteractions(labelService);
    }

    @Test
    void listByWorkspaceWithNoLabelsReturns200EmptyArray() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(labelService.getByWorkspace(ACTOR, workspaceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/labels", workspaceId))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listByWorkspaceWithNonUuidIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/labels", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(labelService, never()).getByWorkspace(any(), any());
    }
}
