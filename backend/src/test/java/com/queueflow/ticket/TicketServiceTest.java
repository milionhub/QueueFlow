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
}
