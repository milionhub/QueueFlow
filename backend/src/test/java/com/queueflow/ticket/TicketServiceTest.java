package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
 * against the real database.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, projectRepository, userRepository);
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
                TicketPriority.CRITICAL, projectId, "ECOM", creatorId, assigneeId, timestamp, timestamp));

        verify(projectRepository, never()).save(any());
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
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.update(ticketId, new UpdateTicketRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());
    }

    @Test
    void emptyPatchChangesNothing() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        User originalAssignee = persistedUser(UUID.randomUUID(), workspace);
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = persistedTicket(ticketId, 1L, "Original title", "Original description",
                TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, originalAssignee, OffsetDateTime.now());

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ticketService.update(ticketId, new UpdateTicketRequest());

        assertThat(ticket.getTitle()).isEqualTo("Original title");
        assertThat(ticket.getDescription()).isEqualTo("Original description");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.BACKLOG);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(ticket.getAssignee()).isSameAs(originalAssignee);
        verify(ticketRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateChangesTitleWhenPresent() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Original title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Updated title");

        ticketService.update(ticket.getId(), request);

        assertThat(ticket.getTitle()).isEqualTo("Updated title");
    }

    @Test
    void updateRejectsBlankTitle() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Original title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("   ");

        assertThatThrownBy(() -> ticketService.update(ticket.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(ticket.getTitle()).isEqualTo("Original title");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateRejectsOverlongTitle() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Original title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("a".repeat(256));

        assertThatThrownBy(() -> ticketService.update(ticket.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(ticket.getTitle()).isEqualTo("Original title");
    }

    @Test
    void updateLeavesDescriptionUnchangedWhenOmitted() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", "Original description", TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        ticketService.update(ticket.getId(), new UpdateTicketRequest());

        assertThat(ticket.getDescription()).isEqualTo("Original description");
    }

    @Test
    void updateChangesDescriptionWhenValuePresent() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", "Original description", TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setDescription("Updated description");

        ticketService.update(ticket.getId(), request);

        assertThat(ticket.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void updateClearsDescriptionWhenExplicitlyNull() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", "Original description", TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setDescription(null);

        ticketService.update(ticket.getId(), request);

        assertThat(ticket.getDescription()).isNull();
    }

    @Test
    void updateChangesStatusWhenPresent() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setStatus(TicketStatus.IN_PROGRESS);

        ticketService.update(ticket.getId(), request);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void updateChangesPriorityWhenPresent() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setPriority(TicketPriority.CRITICAL);

        ticketService.update(ticket.getId(), request);

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
    }

    @Test
    void updateLeavesAssigneeUnchangedWhenOmitted() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        User originalAssignee = persistedUser(UUID.randomUUID(), workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L),
                persistedUser(UUID.randomUUID(), workspace), originalAssignee, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        ticketService.update(ticket.getId(), new UpdateTicketRequest());

        assertThat(ticket.getAssignee()).isSameAs(originalAssignee);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateLoadsAndAssignsUserWhenAssigneeIdPresent() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L),
                persistedUser(UUID.randomUUID(), workspace), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UUID newAssigneeId = UUID.randomUUID();
        User newAssignee = persistedUser(newAssigneeId, workspace);
        when(userRepository.findById(newAssigneeId)).thenReturn(Optional.of(newAssignee));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(newAssigneeId);

        TicketResponse response = ticketService.update(ticket.getId(), request);

        assertThat(ticket.getAssignee()).isSameAs(newAssignee);
        assertThat(response.assigneeId()).isEqualTo(newAssigneeId);
    }

    @Test
    void updateUnassignsWhenAssigneeIdExplicitlyNull() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        User originalAssignee = persistedUser(UUID.randomUUID(), workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L),
                persistedUser(UUID.randomUUID(), workspace), originalAssignee, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(null);

        ticketService.update(ticket.getId(), request);

        assertThat(ticket.getAssignee()).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateThrowsResourceNotFoundExceptionWhenAssigneeMissing() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", workspace, 2L),
                persistedUser(UUID.randomUUID(), workspace), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UUID missingAssigneeId = UUID.randomUUID();
        when(userRepository.findById(missingAssigneeId)).thenReturn(Optional.empty());

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(missingAssigneeId);

        assertThatThrownBy(() -> ticketService.update(ticket.getId(), request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingAssigneeId.toString());

        assertThat(ticket.getAssignee()).isNull();
    }

    @Test
    void updateThrowsBusinessRuleViolationExceptionWhenAssigneeInDifferentWorkspace() {
        Workspace projectWorkspace = persistedWorkspace(UUID.randomUUID());
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", projectWorkspace, 2L),
                persistedUser(UUID.randomUUID(), projectWorkspace), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UUID assigneeId = UUID.randomUUID();
        User assigneeFromOtherWorkspace = persistedUser(assigneeId, otherWorkspace);
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assigneeFromOtherWorkspace));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(assigneeId);

        assertThatThrownBy(() -> ticketService.update(ticket.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Assignee");

        assertThat(ticket.getAssignee()).isNull();
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

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Changed title");
        request.setStatus(TicketStatus.DONE);

        ticketService.update(originalId, request);

        assertThat(ticket.getId()).isEqualTo(originalId);
        assertThat(ticket.getTicketNumber()).isEqualTo(5L);
        assertThat(ticket.getProject()).isSameAs(project);
        assertThat(ticket.getCreator()).isSameAs(creator);
        assertThat(ticket.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void updateDoesNotCallTicketRepositorySave() {
        Ticket ticket = persistedTicket(UUID.randomUUID(), 1L, "Title", null, TicketStatus.BACKLOG,
                TicketPriority.LOW, persistedProject(UUID.randomUUID(), "ECOM", persistedWorkspace(UUID.randomUUID()), 2L),
                persistedUser(UUID.randomUUID(), persistedWorkspace(UUID.randomUUID())), null, OffsetDateTime.now());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Changed title");

        ticketService.update(ticket.getId(), request);

        verify(ticketRepository, never()).save(any());
    }
}
