package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.activity.ActivityService;
import com.queueflow.activity.ActivityType;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.ticket.dto.UpdateTicketRequest;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;

/**
 * Fast unit tests with mocked repositories - real DB row-locking and
 * concurrency behavior are covered separately by
 * TicketConcurrencyIntegrationTest and TicketCreationRollbackIntegrationTest
 * against the real database, and real Activity persistence/atomicity by
 * TicketActivityIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityService activityService;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, projectRepository, userRepository, activityService);
    }

    private static Workspace persistedWorkspace(UUID id) {
        Workspace workspace = new Workspace("Acme Inc.");
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static Project persistedProject(UUID id, String key, Workspace workspace, long nextTicketNumber) {
        Project project = new Project("Project", key, null, workspace);
        ReflectionTestUtils.setField(project, "id", id);
        ReflectionTestUtils.setField(project, "nextTicketNumber", nextTicketNumber);
        return project;
    }

    private static User persistedUser(UUID id, Workspace workspace) {
        User user = new User("User " + id, "user-" + id + "@example.com", "hash", UserRole.MEMBER, workspace);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static CreateTicketRequest requestFor(UUID projectId, UUID creatorId, UUID assigneeId) {
        return new CreateTicketRequest(projectId, "Fix checkout bug", "Details", TicketStatus.BACKLOG,
                TicketPriority.HIGH, creatorId, assigneeId);
    }

    private static Ticket persistedTicket(UUID id, long ticketNumber, String title, String description,
            TicketStatus status, TicketPriority priority, Project project, User creator, User assignee,
            OffsetDateTime timestamp) {
        Ticket ticket = new Ticket(ticketNumber, title, description, status, priority, project, creator, assignee);
        ReflectionTestUtils.setField(ticket, "id", id);
        ReflectionTestUtils.setField(ticket, "createdAt", timestamp);
        ReflectionTestUtils.setField(ticket, "updatedAt", timestamp);
        return ticket;
    }

    /** Fixture bundle shared by most update tests: one workspace, project, creator/actor, and ticket. */
    private record Fixture(Workspace workspace, Project project, User actor, Ticket ticket) {
    }

    private Fixture newFixture(String title, String description, TicketStatus status, TicketPriority priority,
            User assignee) {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L);
        User actor = persistedUser(UUID.randomUUID(), workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, title, description, status, priority, project,
                actor, assignee, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        return new Fixture(workspace, project, actor, ticket);
    }

    // ---------------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------------

    @Test
    void createLoadsProjectUsingLockingMethod() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId);
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 1L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, workspace);

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ticketService.create(requestFor(projectId, creatorId, null));

        verify(projectRepository).findByIdForUpdate(projectId);
        verify(projectRepository, never()).findById(any());
    }

    @Test
    void createThrowsResourceNotFoundExceptionWhenProjectMissing() {
        UUID projectId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.create(requestFor(projectId, creatorId, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(projectId.toString());

        verify(ticketRepository, never()).save(any());
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void createThrowsResourceNotFoundExceptionWhenCreatorMissing() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId);
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 1L);
        UUID creatorId = UUID.randomUUID();

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.create(requestFor(projectId, creatorId, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(creatorId.toString());

        verify(ticketRepository, never()).save(any());
        assertThat(project.getNextTicketNumber()).isEqualTo(1L);
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void createThrowsBusinessRuleViolationExceptionWhenCreatorInDifferentWorkspace() {
        Workspace projectWorkspace = persistedWorkspace(UUID.randomUUID());
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", projectWorkspace, 1L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, otherWorkspace);

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));

        assertThatThrownBy(() -> ticketService.create(requestFor(projectId, creatorId, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Creator");

        verify(ticketRepository, never()).save(any());
        assertThat(project.getNextTicketNumber()).isEqualTo(1L);
    }

    @Test
    void createAcceptsNullAssigneeWithoutLookingUpAUser() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 1L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, workspace);

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = ticketService.create(requestFor(projectId, creatorId, null));

        assertThat(response.assigneeId()).isNull();
        verify(userRepository, times(1)).findById(any());
    }

    @Test
    void createLoadsPresentAssignee() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 1L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, workspace);
        UUID assigneeId = UUID.randomUUID();
        User assignee = persistedUser(assigneeId, workspace);

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assignee));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = ticketService.create(requestFor(projectId, creatorId, assigneeId));

        assertThat(response.assigneeId()).isEqualTo(assigneeId);
        verify(userRepository).findById(assigneeId);
    }

    @Test
    void createThrowsResourceNotFoundExceptionWhenAssigneeMissing() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 1L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, workspace);
        UUID assigneeId = UUID.randomUUID();

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.create(requestFor(projectId, creatorId, assigneeId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(assigneeId.toString());

        verify(ticketRepository, never()).save(any());
        assertThat(project.getNextTicketNumber()).isEqualTo(1L);
    }

    @Test
    void createThrowsBusinessRuleViolationExceptionWhenAssigneeInDifferentWorkspace() {
        Workspace projectWorkspace = persistedWorkspace(UUID.randomUUID());
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", projectWorkspace, 1L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, projectWorkspace);
        UUID assigneeId = UUID.randomUUID();
        User assignee = persistedUser(assigneeId, otherWorkspace);

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assignee));

        assertThatThrownBy(() -> ticketService.create(requestFor(projectId, creatorId, assigneeId)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Assignee");

        verify(ticketRepository, never()).save(any());
        assertThat(project.getNextTicketNumber()).isEqualTo(1L);
    }

    @Test
    void createAllocatesCurrentCounterValueAndIncrementsProject() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 7L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, workspace);

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = ticketService.create(requestFor(projectId, creatorId, null));

        assertThat(response.ticketNumber()).isEqualTo(7L);
        assertThat(project.getNextTicketNumber()).isEqualTo(8L);
    }

    @Test
    void createSavesTicketWithRequestedFieldsAndAllocatedNumber() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 1L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, workspace);
        UUID assigneeId = UUID.randomUUID();
        User assignee = persistedUser(assigneeId, workspace);

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assignee));

        UUID generatedId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket argument = invocation.getArgument(0);
            ReflectionTestUtils.setField(argument, "id", generatedId);
            ReflectionTestUtils.setField(argument, "createdAt", timestamp);
            ReflectionTestUtils.setField(argument, "updatedAt", timestamp);
            return argument;
        });

        CreateTicketRequest request = new CreateTicketRequest(projectId, "Fix checkout bug", "Details",
                TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL, creatorId, assigneeId);

        TicketResponse response = ticketService.create(request);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket saved = captor.getValue();
        assertThat(saved.getTicketNumber()).isEqualTo(1L);
        assertThat(saved.getTitle()).isEqualTo("Fix checkout bug");
        assertThat(saved.getDescription()).isEqualTo("Details");
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(saved.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(saved.getProject()).isSameAs(project);
        assertThat(saved.getCreator()).isSameAs(creator);
        assertThat(saved.getAssignee()).isSameAs(assignee);

        assertThat(response).isEqualTo(new TicketResponse(
                generatedId, 1L, "ECOM-1", "Fix checkout bug", "Details", TicketStatus.IN_PROGRESS,
                TicketPriority.CRITICAL, projectId, "ECOM", creatorId, assigneeId, timestamp, timestamp,
                List.of()));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void createRecordsTicketCreatedActivityWithCreatorAsActorAndNoOldNewValues() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 1L);
        UUID creatorId = UUID.randomUUID();
        User creator = persistedUser(creatorId, workspace);

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = ticketService.create(requestFor(projectId, creatorId, null));

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(activityService).recordActivity(
                eq(ActivityType.TICKET_CREATED), isNull(), isNull(), ticketCaptor.capture(), eq(creator));
        assertThat(ticketCaptor.getValue().getId()).isEqualTo(response.id());
    }

    @Test
    void createRejectsBlankTitleBeforeLockingProjectOrPersistingAnything() {
        CreateTicketRequest request = new CreateTicketRequest(UUID.randomUUID(), "   ", null,
                TicketStatus.BACKLOG, TicketPriority.HIGH, UUID.randomUUID(), null);

        assertThatThrownBy(() -> ticketService.create(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("title");

        verify(projectRepository, never()).findByIdForUpdate(any());
        verify(ticketRepository, never()).save(any());
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsMissingTitle() {
        CreateTicketRequest request = new CreateTicketRequest(UUID.randomUUID(), null, null,
                TicketStatus.BACKLOG, TicketPriority.HIGH, UUID.randomUUID(), null);

        assertThatThrownBy(() -> ticketService.create(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("title");

        verify(ticketRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // READ
    // ---------------------------------------------------------------

    @Test
    void getByIdReturnsMappedResponse() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = persistedTicket(ticketId, 1L, "Fix bug", "Details", TicketStatus.BACKLOG,
                TicketPriority.LOW, project, creator, null, OffsetDateTime.now());

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.getById(ticketId);

        assertThat(response.id()).isEqualTo(ticketId);
        assertThat(response.displayKey()).isEqualTo("ECOM-1");
    }

    @Test
    void getByIdThrowsResourceNotFoundExceptionWhenMissing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getById(ticketId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());
    }

    @Test
    void getByProjectAndNumberReturnsMappedResponse() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 2L);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Fix bug", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, project, creator, null, OffsetDateTime.now());

        when(ticketRepository.findByProjectIdAndTicketNumber(projectId, 1L)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.getByProjectAndNumber(projectId, 1L);

        assertThat(response.ticketNumber()).isEqualTo(1L);
        assertThat(response.projectId()).isEqualTo(projectId);
    }

    @Test
    void getByProjectAndNumberThrowsResourceNotFoundExceptionWhenMissing() {
        UUID projectId = UUID.randomUUID();
        when(ticketRepository.findByProjectIdAndTicketNumber(projectId, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getByProjectAndNumber(projectId, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(projectId.toString())
                .hasMessageContaining("5");
    }

    // ---------------------------------------------------------------
    // UPDATE
    // ---------------------------------------------------------------

    @Test
    void updateThrowsResourceNotFoundExceptionWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.update(ticketId, actorId, new UpdateTicketRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());
    }

    @Test
    void updateThrowsResourceNotFoundExceptionWhenActorMissing() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, project, creator, null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        UUID missingActorId = UUID.randomUUID();
        when(userRepository.findById(missingActorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.update(ticket.getId(), missingActorId, new UpdateTicketRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingActorId.toString());
    }

    @Test
    void updateThrowsBusinessRuleViolationExceptionWhenActorInDifferentWorkspace() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, project, creator, null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        User outsider = persistedUser(UUID.randomUUID(), otherWorkspace);
        when(userRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> ticketService.update(ticket.getId(), outsider.getId(), new UpdateTicketRequest()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void emptyPatchChangesNothingAndRecordsNoActivity() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        User originalAssignee = persistedUser(UUID.randomUUID(), workspace);
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = persistedTicket(ticketId, 1L, "Original title", "Original description",
                TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, originalAssignee, OffsetDateTime.now());

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));

        ticketService.update(ticketId, creator.getId(), new UpdateTicketRequest());

        assertThat(ticket.getTitle()).isEqualTo("Original title");
        assertThat(ticket.getDescription()).isEqualTo("Original description");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.BACKLOG);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(ticket.getAssignee()).isSameAs(originalAssignee);
        verify(ticketRepository, never()).save(any());
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateChangesTitleAndRecordsTitleChangedActivity() {
        Fixture fixture = newFixture("Original title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Updated title");

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getTitle()).isEqualTo("Updated title");
        verify(activityService).recordActivity(ActivityType.TITLE_CHANGED, "Original title", "Updated title",
                fixture.ticket(), fixture.actor());
    }

    @Test
    void updateSettingTitleToSameValueRecordsNoActivity() {
        Fixture fixture = newFixture("Same title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Same title");

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateRejectsBlankTitle() {
        Fixture fixture = newFixture("Original title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("   ");

        assertThatThrownBy(() -> ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(fixture.ticket().getTitle()).isEqualTo("Original title");
        verify(ticketRepository, never()).save(any());
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateRejectsOverlongTitle() {
        Fixture fixture = newFixture("Original title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("a".repeat(256));

        assertThatThrownBy(() -> ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(fixture.ticket().getTitle()).isEqualTo("Original title");
    }

    @Test
    void updateLeavesDescriptionUnchangedWhenOmitted() {
        Fixture fixture = newFixture("Title", "Original description", TicketStatus.BACKLOG, TicketPriority.LOW, null);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), new UpdateTicketRequest());

        assertThat(fixture.ticket().getDescription()).isEqualTo("Original description");
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateChangesDescriptionAndRecordsDescriptionChangedActivity() {
        Fixture fixture = newFixture("Title", "Original description", TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setDescription("Updated description");

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getDescription()).isEqualTo("Updated description");
        verify(activityService).recordActivity(ActivityType.DESCRIPTION_CHANGED, "Original description",
                "Updated description", fixture.ticket(), fixture.actor());
    }

    @Test
    void updateClearingDescriptionRecordsActivityWithNullNewValue() {
        Fixture fixture = newFixture("Title", "Original description", TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setDescription(null);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getDescription()).isNull();
        verify(activityService).recordActivity(ActivityType.DESCRIPTION_CHANGED, "Original description", null,
                fixture.ticket(), fixture.actor());
    }

    @Test
    void updateSettingDescriptionToSameValueRecordsNoActivity() {
        Fixture fixture = newFixture("Title", "Same description", TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setDescription("Same description");

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateChangesStatusAndRecordsStatusChangedActivityWithEnumNames() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setStatus(TicketStatus.IN_PROGRESS);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(activityService).recordActivity(ActivityType.STATUS_CHANGED, "BACKLOG", "IN_PROGRESS",
                fixture.ticket(), fixture.actor());
    }

    @Test
    void updateSettingStatusToSameValueRecordsNoActivity() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setStatus(TicketStatus.BACKLOG);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateChangesPriorityAndRecordsPriorityChangedActivityWithEnumNames() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setPriority(TicketPriority.CRITICAL);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getPriority()).isEqualTo(TicketPriority.CRITICAL);
        verify(activityService).recordActivity(ActivityType.PRIORITY_CHANGED, "LOW", "CRITICAL",
                fixture.ticket(), fixture.actor());
    }

    @Test
    void updateSettingPriorityToSameValueRecordsNoActivity() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setPriority(TicketPriority.LOW);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateLeavesAssigneeUnchangedWhenOmitted() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        User originalAssignee = persistedUser(UUID.randomUUID(), workspace);
        Fixture fixture = newFixtureInWorkspace(workspace, "Title", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                originalAssignee);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), new UpdateTicketRequest());

        assertThat(fixture.ticket().getAssignee()).isSameAs(originalAssignee);
        verify(userRepository, never()).findById(originalAssignee.getId());
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateLoadsAndAssignsUserAndRecordsAssigneeChangedActivityWithUuidStrings() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);
        UUID newAssigneeId = UUID.randomUUID();
        User newAssignee = persistedUser(newAssigneeId, fixture.workspace());
        when(userRepository.findById(newAssigneeId)).thenReturn(Optional.of(newAssignee));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(newAssigneeId);

        TicketResponse response = ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getAssignee()).isSameAs(newAssignee);
        assertThat(response.assigneeId()).isEqualTo(newAssigneeId);
        verify(activityService).recordActivity(ActivityType.ASSIGNEE_CHANGED, null, newAssigneeId.toString(),
                fixture.ticket(), fixture.actor());
    }

    @Test
    void updateReassigningRecordsOldAndNewAssigneeUuidStrings() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        User originalAssignee = persistedUser(UUID.randomUUID(), workspace);
        Fixture fixture = newFixtureInWorkspace(workspace, "Title", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                originalAssignee);
        UUID newAssigneeId = UUID.randomUUID();
        User newAssignee = persistedUser(newAssigneeId, workspace);
        when(userRepository.findById(newAssigneeId)).thenReturn(Optional.of(newAssignee));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(newAssigneeId);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        verify(activityService).recordActivity(ActivityType.ASSIGNEE_CHANGED,
                originalAssignee.getId().toString(), newAssigneeId.toString(), fixture.ticket(), fixture.actor());
    }

    @Test
    void updateAssigningToSameCurrentAssigneeRecordsNoActivityAndDoesNotLookUpUser() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        User originalAssignee = persistedUser(UUID.randomUUID(), workspace);
        Fixture fixture = newFixtureInWorkspace(workspace, "Title", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                originalAssignee);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(originalAssignee.getId());

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getAssignee()).isSameAs(originalAssignee);
        verify(userRepository, never()).findById(originalAssignee.getId());
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateUnassigningRecordsAssigneeChangedActivityWithNullNewValue() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        User originalAssignee = persistedUser(UUID.randomUUID(), workspace);
        Fixture fixture = newFixtureInWorkspace(workspace, "Title", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                originalAssignee);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(null);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getAssignee()).isNull();
        verify(activityService).recordActivity(ActivityType.ASSIGNEE_CHANGED,
                originalAssignee.getId().toString(), null, fixture.ticket(), fixture.actor());
    }

    @Test
    void updateUnassigningAlreadyUnassignedTicketRecordsNoActivity() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(null);

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        assertThat(fixture.ticket().getAssignee()).isNull();
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void updateThrowsResourceNotFoundExceptionWhenAssigneeMissing() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);
        UUID missingAssigneeId = UUID.randomUUID();
        when(userRepository.findById(missingAssigneeId)).thenReturn(Optional.empty());

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(missingAssigneeId);

        assertThatThrownBy(() -> ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingAssigneeId.toString());

        assertThat(fixture.ticket().getAssignee()).isNull();
    }

    @Test
    void updateThrowsBusinessRuleViolationExceptionWhenAssigneeInDifferentWorkspace() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        UUID assigneeId = UUID.randomUUID();
        User assigneeFromOtherWorkspace = persistedUser(assigneeId, otherWorkspace);
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assigneeFromOtherWorkspace));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(assigneeId);

        assertThatThrownBy(() -> ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Assignee");

        assertThat(fixture.ticket().getAssignee()).isNull();
    }

    @Test
    void updateNeverChangesImmutableFields() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        Ticket ticket = persistedTicket(UUID.randomUUID(), 5L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, project, creator, null, createdAt);
        UUID originalId = ticket.getId();
        when(ticketRepository.findById(originalId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Changed title");
        request.setStatus(TicketStatus.DONE);

        ticketService.update(originalId, creator.getId(), request);

        assertThat(ticket.getId()).isEqualTo(originalId);
        assertThat(ticket.getTicketNumber()).isEqualTo(5L);
        assertThat(ticket.getProject()).isSameAs(project);
        assertThat(ticket.getCreator()).isSameAs(creator);
        assertThat(ticket.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void updateDoesNotCallTicketRepositorySave() {
        Fixture fixture = newFixture("Title", null, TicketStatus.BACKLOG, TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Changed title");

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateWithMultipleChangedFieldsRecordsOneActivityPerActualChange() {
        Fixture fixture = newFixture("Original title", "Original description", TicketStatus.BACKLOG,
                TicketPriority.LOW, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Updated title");
        request.setStatus(TicketStatus.IN_PROGRESS);
        // priority and description intentionally left unset -> no activity for those

        ticketService.update(fixture.ticket().getId(), fixture.actor().getId(), request);

        verify(activityService).recordActivity(ActivityType.TITLE_CHANGED, "Original title", "Updated title",
                fixture.ticket(), fixture.actor());
        verify(activityService).recordActivity(ActivityType.STATUS_CHANGED, "BACKLOG", "IN_PROGRESS",
                fixture.ticket(), fixture.actor());
        verify(activityService, times(2)).recordActivity(any(), any(), any(), any(), any());
    }

    private Fixture newFixtureInWorkspace(Workspace workspace, String title, String description,
            TicketStatus status, TicketPriority priority, User assignee) {
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L);
        User actor = persistedUser(UUID.randomUUID(), workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, title, description, status, priority, project,
                actor, assignee, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        return new Fixture(workspace, project, actor, ticket);
    }

    // ---------------------------------------------------------------
    // LIST BY PROJECT
    // ---------------------------------------------------------------

    @Test
    void getByProjectReturnsMappedTicketsPreservingRepositoryOrder() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "ECOM", workspace, 4L);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        Ticket two = persistedTicket(UUID.randomUUID(), 2L, "Second", null, TicketStatus.TODO,
                TicketPriority.LOW, project, creator, null, timestamp);
        Ticket one = persistedTicket(UUID.randomUUID(), 1L, "First", "Details", TicketStatus.BACKLOG,
                TicketPriority.HIGH, project, creator, creator, timestamp);
        when(projectRepository.existsById(projectId)).thenReturn(true);
        // Deliberately not number-sorted: the service must not re-sort.
        when(ticketRepository.findByProjectIdOrderByTicketNumberAsc(projectId)).thenReturn(List.of(two, one));

        List<TicketResponse> responses = ticketService.getByProject(projectId);

        assertThat(responses).extracting(TicketResponse::id).containsExactly(two.getId(), one.getId());
        assertThat(responses.get(1)).isEqualTo(new TicketResponse(one.getId(), 1L, "ECOM-1", "First", "Details",
                TicketStatus.BACKLOG, TicketPriority.HIGH, projectId, "ECOM", creator.getId(), creator.getId(),
                timestamp, timestamp, List.of()));
    }

    @Test
    void getByProjectReturnsEmptyListForExistingProjectWithoutTickets() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(ticketRepository.findByProjectIdOrderByTicketNumberAsc(projectId)).thenReturn(List.of());

        assertThat(ticketService.getByProject(projectId)).isEmpty();
    }

    @Test
    void getByProjectThrowsResourceNotFoundExceptionForUnknownProjectWithoutQueryingTickets() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThatThrownBy(() -> ticketService.getByProject(projectId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(projectId.toString());

        verify(ticketRepository, never()).findByProjectIdOrderByTicketNumberAsc(any());
    }
}
