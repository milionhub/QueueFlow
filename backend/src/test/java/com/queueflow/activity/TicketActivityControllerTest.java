package com.queueflow.activity;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.queueflow.activity.dto.ActivityResponse;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.security.WebSecurityTestConfiguration;
import com.queueflow.security.WithAuthenticatedUser;
import com.queueflow.user.UserRole;

/**
 * Web-layer slice with ActivityService mocked, same approach as the other
 * controller tests. Activity recording itself is service behavior, covered
 * by ActivityServiceTest / TicketActivityIntegrationTest, not here.
 */
@WebMvcTest(TicketActivityController.class)
@Import(WebSecurityTestConfiguration.class)
@WithAuthenticatedUser
class TicketActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** The principal @WithAuthenticatedUser installs: the only possible acting user. */
    private static final AuthenticatedUser ACTOR = new AuthenticatedUser(
            UUID.fromString(WithAuthenticatedUser.USER_ID), UUID.fromString(WithAuthenticatedUser.WORKSPACE_ID),
            UserRole.ADMIN);

    @MockitoBean
    private ActivityService activityService;

    private static final UUID TICKET_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static ActivityResponse activity(UUID id, ActivityType type, String oldValue, String newValue,
            String createdAt) {
        return new ActivityResponse(id, type, oldValue, newValue, TICKET_ID, USER_ID, "Ada Lovelace",
                OffsetDateTime.parse(createdAt));
    }

    @Test
    void getReturns200ArrayInExactlyTheServiceProvidedOrderWithFieldsSerialized() throws Exception {
        UUID created = UUID.randomUUID();
        UUID statusChanged = UUID.randomUUID();
        UUID labelAdded = UUID.randomUUID();
        // Deliberately NOT sorted by createdAt: if the controller re-sorted
        // anything, the JSON order would differ from this list.
        when(activityService.getByTicket(ACTOR, TICKET_ID)).thenReturn(List.of(
                activity(created, ActivityType.TICKET_CREATED, null, null, "2026-09-23T10:00:00Z"),
                activity(statusChanged, ActivityType.STATUS_CHANGED, "BACKLOG", "IN_PROGRESS",
                        "2026-09-23T12:00:00Z"),
                activity(labelAdded, ActivityType.LABEL_ADDED, null, "Backend", "2026-09-23T11:00:00Z")));

        mockMvc.perform(get("/api/tickets/{ticketId}/activities", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(created.toString()))
                .andExpect(jsonPath("$[1].id").value(statusChanged.toString()))
                .andExpect(jsonPath("$[2].id").value(labelAdded.toString()))
                // Fields of a typical change entry.
                .andExpect(jsonPath("$[1].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[1].oldValue").value("BACKLOG"))
                .andExpect(jsonPath("$[1].newValue").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].ticketId").value(TICKET_ID.toString()))
                .andExpect(jsonPath("$[1].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$[1].userName").value("Ada Lovelace"))
                .andExpect(jsonPath("$[1].createdAt").isNotEmpty())
                // Null old/new values are serialized as explicit JSON nulls,
                // not dropped: TICKET_CREATED has neither, LABEL_ADDED no old.
                .andExpect(jsonPath("$[0].type").value("TICKET_CREATED"))
                .andExpect(jsonPath("$[0].oldValue").value(nullValue()))
                .andExpect(jsonPath("$[0].newValue").value(nullValue()))
                .andExpect(jsonPath("$[0]", hasKey("oldValue")))
                .andExpect(jsonPath("$[0]", hasKey("newValue")))
                .andExpect(jsonPath("$[2].oldValue").value(nullValue()))
                .andExpect(jsonPath("$[2].newValue").value("Backend"))
                // Only DTO fields: no nested Ticket/User entity, no password data.
                .andExpect(jsonPath("$[*].ticket").doesNotExist())
                .andExpect(jsonPath("$[*].user").doesNotExist())
                .andExpect(jsonPath("$[*].passwordHash").doesNotExist());

        verify(activityService).getByTicket(ACTOR, TICKET_ID);
        verifyNoMoreInteractions(activityService);
    }

    @Test
    void getWithNoActivitiesReturns200EmptyArray() throws Exception {
        when(activityService.getByTicket(ACTOR, TICKET_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/{ticketId}/activities", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getWithNonUuidTicketIdIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketId}/activities", "BACK-1"))
                .andExpect(status().isBadRequest());

        verify(activityService, never()).getByTicket(any(), any());
    }

    @Test
    void writeMethodsAreNotMappedSoActivityHistoryCannotBeManufacturedOrDeleted() throws Exception {
        mockMvc.perform(post("/api/tickets/{ticketId}/activities", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "STATUS_CHANGED", "oldValue": "BACKLOG", "newValue": "DONE"}
                                """))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/tickets/{ticketId}/activities", TICKET_ID))
                .andExpect(status().isMethodNotAllowed());

        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }
}
