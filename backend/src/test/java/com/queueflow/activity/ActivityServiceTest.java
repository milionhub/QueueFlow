package com.queueflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.activity.dto.ActivityResponse;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.Project;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.user.User;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;

/**
 * Fast unit tests with mocked repositories - real persistence/atomicity is
 * covered separately by TicketActivityIntegrationTest against the real
 * database.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private TicketRepository ticketRepository;

    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(activityRepository, ticketRepository);
    }

    private static Workspace persistedWorkspace(UUID id) {
        Workspace workspace = new Workspace("Acme Inc.");
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static User persistedUser(UUID id, String name, Workspace workspace) {
        User user = new User(name, "user-" + id + "@example.com", "hash", UserRole.MEMBER, workspace);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Ticket persistedTicket(UUID id, Workspace workspace) {
        Project project = new Project("Project", "ECOM", null, workspace);
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        User creator = persistedUser(UUID.randomUUID(), "Creator", workspace);
        Ticket ticket = new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                project, creator, null);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }

    private static Activity persistedActivity(UUID id, ActivityType type, String oldValue, String newValue,
            Ticket ticket, User user, OffsetDateTime createdAt) {
        Activity activity = new Activity(type, oldValue, newValue, ticket, user);
        ReflectionTestUtils.setField(activity, "id", id);
        ReflectionTestUtils.setField(activity, "createdAt", createdAt);
        return activity;
    }

    @Test
    void getByTicketVerifiesTicketExistsBeforeQuerying() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = persistedTicket(ticketId, workspace);
        User user = persistedUser(UUID.randomUUID(), "Ada Lovelace", workspace);
        OffsetDateTime now = OffsetDateTime.now();
        Activity first = persistedActivity(UUID.randomUUID(), ActivityType.TICKET_CREATED, null, null,
                ticket, user, now.minusMinutes(10));
        Activity second = persistedActivity(UUID.randomUUID(), ActivityType.STATUS_CHANGED, "BACKLOG", "TODO",
                ticket, user, now.minusMinutes(5));

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).thenReturn(List.of(first, second));

        List<ActivityResponse> responses = activityService.getByTicket(ticketId);

        assertThat(responses).extracting(ActivityResponse::type)
                .containsExactly(ActivityType.TICKET_CREATED, ActivityType.STATUS_CHANGED);
        verify(ticketRepository).existsById(ticketId);
    }

    @Test
    void getByTicketThrowsResourceNotFoundExceptionWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.existsById(ticketId)).thenReturn(false);

        assertThatThrownBy(() -> activityService.getByTicket(ticketId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());

        verify(activityRepository, never()).findByTicketIdOrderByCreatedAtAscIdAsc(any());
    }

    @Test
    void getByTicketMapsActivityResponseFieldsCorrectly() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = persistedTicket(ticketId, workspace);
        User user = persistedUser(UUID.randomUUID(), "Ada Lovelace", workspace);
        OffsetDateTime now = OffsetDateTime.now();
        UUID activityId = UUID.randomUUID();
        Activity activity = persistedActivity(activityId, ActivityType.STATUS_CHANGED, "BACKLOG", "TODO",
                ticket, user, now);

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).thenReturn(List.of(activity));

        List<ActivityResponse> responses = activityService.getByTicket(ticketId);

        assertThat(responses).containsExactly(new ActivityResponse(
                activityId, ActivityType.STATUS_CHANGED, "BACKLOG", "TODO", ticketId, user.getId(),
                "Ada Lovelace", now));
    }

    @Test
    void recordActivitySavesNewActivityViaRepository() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Ticket ticket = persistedTicket(UUID.randomUUID(), workspace);
        User user = persistedUser(UUID.randomUUID(), "Ada Lovelace", workspace);

        activityService.recordActivity(ActivityType.TICKET_CREATED, null, null, ticket, user);

        verify(activityRepository).save(any(Activity.class));
    }
}
