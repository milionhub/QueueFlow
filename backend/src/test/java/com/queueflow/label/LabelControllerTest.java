package com.queueflow.label;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;

import com.queueflow.config.SecurityConfig;
import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.ticket.dto.TicketResponse;

/**
 * Web-layer slice covering both LabelService-backed controllers
 * (LabelController and TicketLabelController) with LabelService mocked,
 * same approach as the other controller tests: real MVC mapping, bean
 * validation, JSON serialization and the real (temporary) SecurityConfig.
 * Idempotency and Activity recording are service behavior, covered by
 * LabelServiceTest / LabelServiceIntegrationTest, not here.
 */
@WebMvcTest({LabelController.class, TicketLabelController.class})
@Import(SecurityConfig.class)
class LabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LabelService labelService;

    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-09-23T10:15:30Z");

    private static LabelResponse labelResponse(UUID id, String name, UUID workspaceId) {
        return new LabelResponse(id, name, workspaceId, TIMESTAMP, TIMESTAMP);
    }

    private static TicketResponse ticketResponse(UUID ticketId) {
        return new TicketResponse(ticketId, 1L, "BACK-1", "Fix checkout bug", null, TicketStatus.BACKLOG,
                TicketPriority.HIGH, UUID.randomUUID(), "BACK", UUID.randomUUID(), null, TIMESTAMP, TIMESTAMP);
    }

    private void postExpectingBadRequest(String json) throws Exception {
        mockMvc.perform(post("/api/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(labelService, never()).create(any());
    }

    // ---------------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------------

    @Test
    void postValidRequestReturns201WithBodyAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(labelService.create(any())).thenReturn(labelResponse(id, "Backend", workspaceId));

        mockMvc.perform(post("/api/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "Backend"}
                                """.formatted(workspaceId)))
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
        when(labelService.create(any())).thenReturn(labelResponse(UUID.randomUUID(), "Backend", workspaceId));

        mockMvc.perform(post("/api/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId": "%s", "name": "  BackEnd  "}
                                """.formatted(workspaceId)))
                .andExpect(status().isCreated());

        // Name arrives untrimmed and with its original case: normalization
        // is LabelService's job.
        verify(labelService).create(new CreateLabelRequest(workspaceId, "  BackEnd  "));
    }

    @Test
    void postBlankNameIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"workspaceId": "%s", "name": "   "}
                """.formatted(UUID.randomUUID()));
    }

    @Test
    void postMissingWorkspaceIdIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"name": "Backend"}
                """);
    }

    @Test
    void postOversizedNameIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"workspaceId": "%s", "name": "%s"}
                """.formatted(UUID.randomUUID(), "a".repeat(51)));
    }

    // ---------------------------------------------------------------
    // GET BY ID / BY NAME
    // ---------------------------------------------------------------

    @Test
    void getByIdReturns200WithExpectedJsonAndDelegatesExactUuid() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(labelService.getById(id)).thenReturn(labelResponse(id, "Backend", workspaceId));

        mockMvc.perform(get("/api/labels/{labelId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Backend"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.workspace").doesNotExist());

        verify(labelService).getById(id);
    }

    @Test
    void getByNameReturns200AndDelegatesExactWorkspaceIdAndRawName() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        // Mixed case with surrounding spaces: if the controller trimmed or
        // changed case, this stub would not match and verify would fail.
        String rawName = "  BackEnd ";
        when(labelService.getByWorkspaceAndName(workspaceId, rawName))
                .thenReturn(labelResponse(id, "BackEnd", workspaceId));

        mockMvc.perform(get("/api/labels/by-name")
                        .param("workspaceId", workspaceId.toString())
                        .param("name", rawName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("BackEnd"));

        // Also proves "/by-name" is routed to the literal mapping, never
        // captured by "/{labelId}" as a (bad) UUID path variable.
        verify(labelService).getByWorkspaceAndName(workspaceId, rawName);
        verifyNoMoreInteractions(labelService);
    }

    // ---------------------------------------------------------------
    // TICKET-LABEL ASSOCIATIONS
    // ---------------------------------------------------------------

    @Test
    void putAddsLabelReturns200WithTicketResponseAndDelegatesExactIds() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        when(labelService.addLabelToTicket(ticketId, labelId, actorUserId)).thenReturn(ticketResponse(ticketId));

        ResultActions result = mockMvc.perform(put("/api/tickets/{ticketId}/labels/{labelId}", ticketId, labelId)
                        .param("actorUserId", actorUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.displayKey").value("BACK-1"));
        expectNoTicketEntityLeakage(result);

        verify(labelService).addLabelToTicket(ticketId, labelId, actorUserId);
        verifyNoMoreInteractions(labelService);
    }

    @Test
    void putWithoutActorUserIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(put("/api/tickets/{ticketId}/labels/{labelId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verify(labelService, never()).addLabelToTicket(any(), any(), any());
    }

    @Test
    void deleteRemovesLabelReturns200WithTicketResponseAndDelegatesExactIds() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        when(labelService.removeLabelFromTicket(ticketId, labelId, actorUserId))
                .thenReturn(ticketResponse(ticketId));

        ResultActions result = mockMvc.perform(
                        delete("/api/tickets/{ticketId}/labels/{labelId}", ticketId, labelId)
                                .param("actorUserId", actorUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()));
        expectNoTicketEntityLeakage(result);

        verify(labelService).removeLabelFromTicket(ticketId, labelId, actorUserId);
        verifyNoMoreInteractions(labelService);
    }

    @Test
    void deleteWithoutActorUserIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(delete("/api/tickets/{ticketId}/labels/{labelId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verify(labelService, never()).removeLabelFromTicket(any(), any(), any());
    }

    private static void expectNoTicketEntityLeakage(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.project").doesNotExist())
                .andExpect(jsonPath("$.creator").doesNotExist())
                .andExpect(jsonPath("$.assignee").doesNotExist())
                .andExpect(jsonPath("$.labels").doesNotExist());
    }
}
