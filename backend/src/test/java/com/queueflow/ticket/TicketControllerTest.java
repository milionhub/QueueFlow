package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.test.web.servlet.ResultActions;

import com.queueflow.config.SecurityConfig;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.ticket.dto.UpdateTicketRequest;

/**
 * Web-layer slice with TicketService mocked, same approach as
 * ProjectControllerTest. The PATCH presence-semantics tests are the key
 * addition: they send REAL JSON through Spring MVC's Jackson message
 * converter and capture the UpdateTicketRequest the service receives,
 * proving omitted vs. explicit-null survives deserialization - something
 * TicketServiceTest (which builds the DTO by hand) cannot prove.
 */
@WebMvcTest({TicketController.class, ProjectTicketController.class})
@Import(SecurityConfig.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID ASSIGNEE_ID = UUID.randomUUID();

    private static TicketResponse ticketResponse(UUID id, TicketStatus status, TicketPriority priority,
            String description, UUID assigneeId) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        return new TicketResponse(id, 1L, "BACK-1", "Fix checkout bug", description, status, priority,
                PROJECT_ID, "BACK", CREATOR_ID, assigneeId, timestamp, timestamp, List.of());
    }

    private static TicketResponse ticketResponse(UUID id) {
        return ticketResponse(id, TicketStatus.BACKLOG, TicketPriority.HIGH, "Details", ASSIGNEE_ID);
    }

    /** Asserts that no JPA entity/association object leaks into the JSON - only ids. */
    private static void expectNoEntityLeakage(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.project").doesNotExist())
                .andExpect(jsonPath("$.creator").doesNotExist())
                .andExpect(jsonPath("$.assignee").doesNotExist())
                // labels is a LabelResponse DTO array, never Label entities.
                .andExpect(jsonPath("$.labels").isArray())
                .andExpect(jsonPath("$.labels[*].workspace").doesNotExist());
    }

    private UpdateTicketRequest patchAndCaptureRequest(UUID ticketId, UUID actorUserId, String json)
            throws Exception {
        when(ticketService.update(eq(ticketId), eq(actorUserId), any())).thenReturn(ticketResponse(ticketId));

        mockMvc.perform(patch("/api/tickets/{ticketId}", ticketId)
                        .param("actorUserId", actorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateTicketRequest> captor = ArgumentCaptor.forClass(UpdateTicketRequest.class);
        verify(ticketService).update(eq(ticketId), eq(actorUserId), captor.capture());
        return captor.getValue();
    }

    private void postExpectingBadRequest(String json) throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(ticketService, never()).create(any());
    }

    // ---------------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------------

    @Test
    void postValidRequestReturns201WithBodyAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketService.create(any())).thenReturn(ticketResponse(id));

        ResultActions result = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId": "%s", "title": "Fix checkout bug", "description": "Details",
                                 "status": "BACKLOG", "priority": "HIGH",
                                 "creatorId": "%s", "assigneeId": "%s"}
                                """.formatted(PROJECT_ID, CREATOR_ID, ASSIGNEE_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tickets/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.ticketNumber").value(1))
                .andExpect(jsonPath("$.displayKey").value("BACK-1"))
                .andExpect(jsonPath("$.title").value("Fix checkout bug"))
                .andExpect(jsonPath("$.description").value("Details"))
                .andExpect(jsonPath("$.status").value("BACKLOG"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.projectKey").value("BACK"))
                .andExpect(jsonPath("$.creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.assigneeId").value(ASSIGNEE_ID.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        expectNoEntityLeakage(result);
    }

    @Test
    void postDelegatesExactDeserializedRequestToService() throws Exception {
        when(ticketService.create(any())).thenReturn(ticketResponse(UUID.randomUUID()));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId": "%s", "title": "Fix checkout bug", "description": "Details",
                                 "status": "BACKLOG", "priority": "HIGH",
                                 "creatorId": "%s", "assigneeId": "%s"}
                                """.formatted(PROJECT_ID, CREATOR_ID, ASSIGNEE_ID)))
                .andExpect(status().isCreated());

        verify(ticketService).create(new CreateTicketRequest(PROJECT_ID, "Fix checkout bug", "Details",
                TicketStatus.BACKLOG, TicketPriority.HIGH, CREATOR_ID, ASSIGNEE_ID));
    }

    @Test
    void postBlankTitleIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"projectId": "%s", "title": "   ", "status": "BACKLOG", "priority": "HIGH", "creatorId": "%s"}
                """.formatted(PROJECT_ID, CREATOR_ID));
    }

    @Test
    void postMissingProjectIdIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"title": "Fix checkout bug", "status": "BACKLOG", "priority": "HIGH", "creatorId": "%s"}
                """.formatted(CREATOR_ID));
    }

    @Test
    void postMissingCreatorIdIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"projectId": "%s", "title": "Fix checkout bug", "status": "BACKLOG", "priority": "HIGH"}
                """.formatted(PROJECT_ID));
    }

    @Test
    void postMissingPriorityIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"projectId": "%s", "title": "Fix checkout bug", "status": "BACKLOG", "creatorId": "%s"}
                """.formatted(PROJECT_ID, CREATOR_ID));
    }

    @Test
    void postUnknownStatusValueIsRejectedWithoutCallingService() throws Exception {
        postExpectingBadRequest("""
                {"projectId": "%s", "title": "Fix checkout bug", "status": "NOT_A_STATUS", "priority": "HIGH",
                 "creatorId": "%s"}
                """.formatted(PROJECT_ID, CREATOR_ID));
    }

    // ---------------------------------------------------------------
    // GET BY ID / BY PROJECT + NUMBER
    // ---------------------------------------------------------------

    @Test
    void getByIdReturns200WithExpectedJsonAndDelegatesExactUuid() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketService.getById(id)).thenReturn(ticketResponse(id));

        ResultActions result = mockMvc.perform(get("/api/tickets/{ticketId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.displayKey").value("BACK-1"))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()));
        expectNoEntityLeakage(result);

        verify(ticketService).getById(id);
    }

    @Test
    void labelsSerializeAsNestedLabelDtosInServiceProvidedOrder() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID api = UUID.randomUUID();
        UUID bug = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        TicketResponse withLabels = new TicketResponse(id, 1L, "BACK-1", "Fix checkout bug", null,
                TicketStatus.TODO, TicketPriority.LOW, PROJECT_ID, "BACK", CREATOR_ID, null, timestamp, timestamp,
                List.of(new LabelResponse(api, "api", workspaceId, timestamp, timestamp),
                        new LabelResponse(bug, "Bug", workspaceId, timestamp, timestamp)));
        when(ticketService.getById(id)).thenReturn(withLabels);

        mockMvc.perform(get("/api/tickets/{ticketId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(2))
                .andExpect(jsonPath("$.labels[0].id").value(api.toString()))
                .andExpect(jsonPath("$.labels[0].name").value("api"))
                .andExpect(jsonPath("$.labels[0].workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.labels[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.labels[1].name").value("Bug"))
                .andExpect(jsonPath("$.labels[*].workspace").doesNotExist())
                // Unchanged top-level fields alongside the new one.
                .andExpect(jsonPath("$.displayKey").value("BACK-1"))
                .andExpect(jsonPath("$.creatorId").value(CREATOR_ID.toString()));
    }

    // ---------------------------------------------------------------
    // GET BY PROJECT + NUMBER (ProjectTicketController)
    // ---------------------------------------------------------------

    @Test
    void getByProjectAndNumberReturns200AndDelegatesExactProjectIdAndTicketNumber() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketService.getByProjectAndNumber(PROJECT_ID, 42L)).thenReturn(ticketResponse(id));

        mockMvc.perform(get("/api/projects/{projectId}/tickets/{ticketNumber}", PROJECT_ID, 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.displayKey").value("BACK-1"))
                .andExpect(jsonPath("$.labels").isArray());

        verify(ticketService).getByProjectAndNumber(PROJECT_ID, 42L);
        verifyNoMoreInteractions(ticketService);
    }

    @Test
    void projectTicketCollectionAndSingleTicketRoutesDoNotConflict() throws Exception {
        when(ticketService.getByProject(PROJECT_ID)).thenReturn(List.of());
        when(ticketService.getByProjectAndNumber(PROJECT_ID, 7L)).thenReturn(ticketResponse(UUID.randomUUID()));

        mockMvc.perform(get("/api/projects/{projectId}/tickets", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/projects/{projectId}/tickets/{ticketNumber}", PROJECT_ID, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());

        // Each URL reached exactly its own handler, once.
        verify(ticketService).getByProject(PROJECT_ID);
        verify(ticketService).getByProjectAndNumber(PROJECT_ID, 7L);
        verifyNoMoreInteractions(ticketService);
    }

    @Test
    void getByProjectAndNonNumericTicketNumberIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/tickets/{ticketNumber}", PROJECT_ID, "BACK-1"))
                .andExpect(status().isBadRequest());

        verify(ticketService, never()).getByProjectAndNumber(any(), anyLong());
    }

    @Test
    void removedByKeyRouteIsNoLongerATicketLookup() throws Exception {
        // No mapping exists for /api/tickets/by-key any more: the path now
        // only matches GET /api/tickets/{ticketId}, where "by-key" is not a
        // UUID -> 400 before any service call. Nothing looks a ticket up.
        mockMvc.perform(get("/api/tickets/by-key")
                        .param("projectId", PROJECT_ID.toString())
                        .param("ticketNumber", "1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketService);
    }

    // ---------------------------------------------------------------
    // PATCH
    // ---------------------------------------------------------------

    @Test
    void patchReturns200WithResponseAndDelegatesIdsAndValuesExactly() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID newAssigneeId = UUID.randomUUID();
        when(ticketService.update(eq(ticketId), eq(actorUserId), any())).thenReturn(
                ticketResponse(ticketId, TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL, "New", newAssigneeId));

        ResultActions result = mockMvc.perform(patch("/api/tickets/{ticketId}", ticketId)
                        .param("actorUserId", actorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New title", "description": "New", "status": "IN_PROGRESS",
                                 "priority": "CRITICAL", "assigneeId": "%s"}
                                """.formatted(newAssigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.description").value("New"))
                .andExpect(jsonPath("$.assigneeId").value(newAssigneeId.toString()));
        expectNoEntityLeakage(result);

        ArgumentCaptor<UpdateTicketRequest> captor = ArgumentCaptor.forClass(UpdateTicketRequest.class);
        verify(ticketService).update(eq(ticketId), eq(actorUserId), captor.capture());
        UpdateTicketRequest request = captor.getValue();
        assertThat(request.getTitle()).isEqualTo("New title");
        assertThat(request.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(request.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(request.descriptionPatch().isPresent()).isTrue();
        assertThat(request.descriptionPatch().value()).isEqualTo("New");
        assertThat(request.assigneeIdPatch().isPresent()).isTrue();
        assertThat(request.assigneeIdPatch().value()).isEqualTo(newAssigneeId);
    }

    @Test
    void patchWithoutActorUserIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/tickets/{ticketId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """))
                .andExpect(status().isBadRequest());

        verify(ticketService, never()).update(any(), any(), any());
    }

    @Test
    void patchOverlongTitleIsRejectedByValidationWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/tickets/{ticketId}", UUID.randomUUID())
                        .param("actorUserId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(ticketService, never()).update(any(), any(), any());
    }

    // ---------------------------------------------------------------
    // PATCH - Jackson omitted vs. explicit-null presence semantics
    // ---------------------------------------------------------------

    @Test
    void patchEmptyObjectLeavesDescriptionAndAssigneeOmitted() throws Exception {
        UpdateTicketRequest request = patchAndCaptureRequest(UUID.randomUUID(), UUID.randomUUID(), "{}");

        assertThat(request.descriptionPatch().isPresent()).isFalse();
        assertThat(request.assigneeIdPatch().isPresent()).isFalse();
        assertThat(request.getTitle()).isNull();
        assertThat(request.getStatus()).isNull();
        assertThat(request.getPriority()).isNull();
    }

    @Test
    void patchExplicitNullDescriptionIsPresentWithNullValueAndAssigneeStaysOmitted() throws Exception {
        UpdateTicketRequest request = patchAndCaptureRequest(UUID.randomUUID(), UUID.randomUUID(), """
                {"description": null}
                """);

        assertThat(request.descriptionPatch().isPresent()).isTrue();
        assertThat(request.descriptionPatch().value()).isNull();
        assertThat(request.assigneeIdPatch().isPresent()).isFalse();
    }

    @Test
    void patchExplicitNullAssigneeIdIsPresentWithNullValueAndDescriptionStaysOmitted() throws Exception {
        UpdateTicketRequest request = patchAndCaptureRequest(UUID.randomUUID(), UUID.randomUUID(), """
                {"assigneeId": null}
                """);

        assertThat(request.assigneeIdPatch().isPresent()).isTrue();
        assertThat(request.assigneeIdPatch().value()).isNull();
        assertThat(request.descriptionPatch().isPresent()).isFalse();
    }

    // ---------------------------------------------------------------
    // LIST BY PROJECT (ProjectTicketController)
    // ---------------------------------------------------------------

    @Test
    void listByProjectReturns200ArrayInServiceOrderAndDelegatesExactProjectId() throws Exception {
        UUID second = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        // Deliberately not number-sorted: the controller must not re-sort.
        when(ticketService.getByProject(PROJECT_ID)).thenReturn(List.of(ticketResponse(second), ticketResponse(first)));

        ResultActions result = mockMvc.perform(get("/api/projects/{projectId}/tickets", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[1].id").value(first.toString()))
                .andExpect(jsonPath("$[0].displayKey").value("BACK-1"))
                .andExpect(jsonPath("$[0].projectId").value(PROJECT_ID.toString()));
        result.andExpect(jsonPath("$[*].project").doesNotExist())
                .andExpect(jsonPath("$[*].creator").doesNotExist())
                .andExpect(jsonPath("$[*].assignee").doesNotExist())
                .andExpect(jsonPath("$[0].labels").isArray())
                .andExpect(jsonPath("$[1].labels").isArray())
                .andExpect(jsonPath("$[*].labels[*].workspace").doesNotExist());

        verify(ticketService).getByProject(PROJECT_ID);
        verifyNoMoreInteractions(ticketService);
    }

    @Test
    void listByProjectWithNoTicketsReturns200EmptyArray() throws Exception {
        when(ticketService.getByProject(PROJECT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/projects/{projectId}/tickets", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listByProjectWithNonUuidIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/tickets", "ECOM"))
                .andExpect(status().isBadRequest());

        verify(ticketService, never()).getByProject(any());
    }
}
