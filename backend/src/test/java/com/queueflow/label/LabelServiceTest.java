package com.queueflow.label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.activity.ActivityService;
import com.queueflow.activity.ActivityType;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.project.Project;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Fast unit tests with mocked repositories - real DB persistence/constraint
 * behavior is covered separately by LabelServiceIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityService activityService;

    private LabelService labelService;

    @BeforeEach
    void setUp() {
        labelService = new LabelService(
                labelRepository, workspaceRepository, ticketRepository, userRepository, activityService);
    }

    private static Workspace persistedWorkspace(UUID id) {
        Workspace workspace = new Workspace("Acme Inc.");
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static Label persistedLabel(UUID id, String name, Workspace workspace) {
        Label label = new Label(name, workspace);
        ReflectionTestUtils.setField(label, "id", id);
        return label;
    }

    private static Project persistedProject(UUID id, String key, Workspace workspace) {
        Project project = new Project("Project", key, null, workspace);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private static User persistedUser(UUID id, Workspace workspace) {
        User user = new User("User " + id, "user-" + id + "@example.com", "hash", UserRole.MEMBER, workspace);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Ticket persistedTicket(UUID id, Project project, User creator) {
        Ticket ticket = new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                project, creator, null);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }

    /** Fixture bundle: one workspace, ticket (with its creator), and an actor also in that workspace. */
    private record Fixture(Workspace workspace, Ticket ticket, User actor) {
    }

    private Fixture newFixture() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        Project project = persistedProject(UUID.randomUUID(), "ECOM", workspace);
        User creator = persistedUser(UUID.randomUUID(), workspace);
        Ticket ticket = persistedTicket(UUID.randomUUID(), project, creator);
        User actor = persistedUser(UUID.randomUUID(), workspace);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        // lenient: some tests using this shared fixture deliberately supply
        // a different/missing actor id and never reach this lookup (e.g.
        // label-missing short-circuits before the actor is loaded).
        org.mockito.Mockito.lenient().when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        return new Fixture(workspace, ticket, actor);
    }

    // ---------------------------------------------------------------
    // CREATE / READ
    // ---------------------------------------------------------------

    @Test
    void createNormalizesWhitespaceAndLoadsCorrectWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(labelRepository.existsByWorkspaceIdAndName(workspaceId, "backend")).thenReturn(false);
        when(labelRepository.save(any(Label.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LabelResponse response = labelService.create(new CreateLabelRequest(workspaceId, "  backend  "));

        assertThat(response.name()).isEqualTo("backend");
        verify(workspaceRepository).findById(workspaceId);
        verify(labelRepository).existsByWorkspaceIdAndName(workspaceId, "backend");
    }

    @Test
    void createPreservesLetterCase() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(labelRepository.existsByWorkspaceIdAndName(workspaceId, "Backend")).thenReturn(false);
        when(labelRepository.save(any(Label.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LabelResponse response = labelService.create(new CreateLabelRequest(workspaceId, "  Backend  "));

        assertThat(response.name()).isEqualTo("Backend");
    }

    @Test
    void createThrowsResourceNotFoundExceptionWhenWorkspaceMissing() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.create(new CreateLabelRequest(workspaceId, "backend")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(workspaceId.toString());

        verify(labelRepository, never()).save(any());
    }

    @Test
    void createThrowsResourceAlreadyExistsExceptionWhenDuplicate() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(labelRepository.existsByWorkspaceIdAndName(workspaceId, "backend")).thenReturn(true);

        assertThatThrownBy(() -> labelService.create(new CreateLabelRequest(workspaceId, "backend")))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("backend");

        verify(labelRepository, never()).save(any());
    }

    @Test
    void getByIdReturnsMappedResponse() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID());
        UUID labelId = UUID.randomUUID();
        Label label = persistedLabel(labelId, "backend", workspace);
        when(labelRepository.findById(labelId)).thenReturn(Optional.of(label));

        LabelResponse response = labelService.getById(labelId);

        assertThat(response.id()).isEqualTo(labelId);
        assertThat(response.name()).isEqualTo("backend");
    }

    @Test
    void getByIdThrowsResourceNotFoundExceptionWhenMissing() {
        UUID labelId = UUID.randomUUID();
        when(labelRepository.findById(labelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.getById(labelId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(labelId.toString());
    }

    @Test
    void getByWorkspaceAndNameTrimsInputBeforeQuerying() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId);
        Label label = persistedLabel(UUID.randomUUID(), "backend", workspace);
        when(labelRepository.findByWorkspaceIdAndName(workspaceId, "backend")).thenReturn(Optional.of(label));

        LabelResponse response = labelService.getByWorkspaceAndName(workspaceId, "  backend  ");

        assertThat(response.name()).isEqualTo("backend");
        verify(labelRepository).findByWorkspaceIdAndName(workspaceId, "backend");
    }

    // ---------------------------------------------------------------
    // ADD
    // ---------------------------------------------------------------

    @Test
    void addLabelToTicketThrowsResourceNotFoundExceptionWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.addLabelToTicket(ticketId, labelId, actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());
    }

    @Test
    void addLabelToTicketThrowsResourceNotFoundExceptionWhenLabelMissing() {
        Fixture fixture = newFixture();
        UUID labelId = UUID.randomUUID();
        when(labelRepository.findById(labelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.addLabelToTicket(fixture.ticket().getId(), labelId, fixture.actor().getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(labelId.toString());
    }

    @Test
    void addLabelToTicketThrowsResourceNotFoundExceptionWhenActorMissing() {
        Fixture fixture = newFixture();
        Label label = persistedLabel(UUID.randomUUID(), "backend", fixture.workspace());
        UUID missingActorId = UUID.randomUUID();
        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));
        when(userRepository.findById(missingActorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.addLabelToTicket(fixture.ticket().getId(), label.getId(), missingActorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingActorId.toString());
    }

    @Test
    void addLabelToTicketThrowsBusinessRuleViolationExceptionWhenActorInDifferentWorkspace() {
        Fixture fixture = newFixture();
        Label label = persistedLabel(UUID.randomUUID(), "backend", fixture.workspace());
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        User outsider = persistedUser(UUID.randomUUID(), otherWorkspace);
        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));
        when(userRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> labelService.addLabelToTicket(fixture.ticket().getId(), label.getId(), outsider.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Actor");

        assertThat(fixture.ticket().getLabels()).isEmpty();
    }

    @Test
    void addLabelToTicketThrowsBusinessRuleViolationExceptionWhenLabelCrossWorkspace() {
        Fixture fixture = newFixture();
        Workspace labelWorkspace = persistedWorkspace(UUID.randomUUID());
        Label label = persistedLabel(UUID.randomUUID(), "backend", labelWorkspace);

        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));

        assertThatThrownBy(() -> labelService.addLabelToTicket(fixture.ticket().getId(), label.getId(), fixture.actor().getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Label");

        assertThat(fixture.ticket().getLabels()).isEmpty();
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }

    @Test
    void addLabelToTicketAttachesLabelReturnsMappedResponseAndRecordsLabelAddedActivity() {
        Fixture fixture = newFixture();
        Label label = persistedLabel(UUID.randomUUID(), "backend", fixture.workspace());
        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));

        TicketResponse response = labelService.addLabelToTicket(
                fixture.ticket().getId(), label.getId(), fixture.actor().getId());

        assertThat(fixture.ticket().getLabels()).extracting(Label::getId).containsExactly(label.getId());
        assertThat(response.id()).isEqualTo(fixture.ticket().getId());
        verify(ticketRepository, never()).save(any());
        verify(activityService).recordActivity(ActivityType.LABEL_ADDED, null, "backend",
                fixture.ticket(), fixture.actor());
    }

    @Test
    void addingSameLabelTwiceRecordsOnlyOneLabelAddedActivityEvenAcrossDifferentLabelInstances() {
        Fixture fixture = newFixture();
        UUID labelId = UUID.randomUUID();
        // Two DISTINCT Label instances representing the same row (same id).
        // Membership must be detected by id, not by object/Set identity.
        Label firstLoad = persistedLabel(labelId, "backend", fixture.workspace());
        Label secondLoad = persistedLabel(labelId, "backend", fixture.workspace());
        when(labelRepository.findById(labelId)).thenReturn(Optional.of(firstLoad), Optional.of(secondLoad));

        labelService.addLabelToTicket(fixture.ticket().getId(), labelId, fixture.actor().getId());
        labelService.addLabelToTicket(fixture.ticket().getId(), labelId, fixture.actor().getId());

        assertThat(fixture.ticket().getLabels()).hasSize(1);
        assertThat(fixture.ticket().getLabels().iterator().next().getId()).isEqualTo(labelId);
        verify(ticketRepository, never()).save(any());
        // Only the first call actually changed the Set, so only one Activity.
        verify(activityService, org.mockito.Mockito.times(1))
                .recordActivity(any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------
    // REMOVE
    // ---------------------------------------------------------------

    @Test
    void removeLabelFromTicketThrowsResourceNotFoundExceptionWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.removeLabelFromTicket(ticketId, labelId, actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ticketId.toString());
    }

    @Test
    void removeLabelFromTicketThrowsResourceNotFoundExceptionWhenLabelMissing() {
        Fixture fixture = newFixture();
        UUID labelId = UUID.randomUUID();
        when(labelRepository.findById(labelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.removeLabelFromTicket(fixture.ticket().getId(), labelId, fixture.actor().getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(labelId.toString());
    }

    @Test
    void removeLabelFromTicketThrowsResourceNotFoundExceptionWhenActorMissing() {
        Fixture fixture = newFixture();
        Label label = persistedLabel(UUID.randomUUID(), "backend", fixture.workspace());
        UUID missingActorId = UUID.randomUUID();
        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));
        when(userRepository.findById(missingActorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.removeLabelFromTicket(fixture.ticket().getId(), label.getId(), missingActorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingActorId.toString());
    }

    @Test
    void removeLabelFromTicketThrowsBusinessRuleViolationExceptionWhenActorInDifferentWorkspace() {
        Fixture fixture = newFixture();
        Label label = persistedLabel(UUID.randomUUID(), "backend", fixture.workspace());
        Workspace otherWorkspace = persistedWorkspace(UUID.randomUUID());
        User outsider = persistedUser(UUID.randomUUID(), otherWorkspace);
        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));
        when(userRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> labelService.removeLabelFromTicket(fixture.ticket().getId(), label.getId(), outsider.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void removeLabelFromTicketThrowsBusinessRuleViolationExceptionWhenLabelCrossWorkspace() {
        Fixture fixture = newFixture();
        Workspace labelWorkspace = persistedWorkspace(UUID.randomUUID());
        Label label = persistedLabel(UUID.randomUUID(), "backend", labelWorkspace);
        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));

        assertThatThrownBy(() -> labelService.removeLabelFromTicket(fixture.ticket().getId(), label.getId(), fixture.actor().getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Label");
    }

    @Test
    void removeLabelFromTicketDetachesLabelReturnsMappedResponseAndRecordsLabelRemovedActivity() {
        Fixture fixture = newFixture();
        Label label = persistedLabel(UUID.randomUUID(), "backend", fixture.workspace());
        fixture.ticket().addLabel(label);
        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));

        TicketResponse response = labelService.removeLabelFromTicket(
                fixture.ticket().getId(), label.getId(), fixture.actor().getId());

        assertThat(fixture.ticket().getLabels()).isEmpty();
        assertThat(response.id()).isEqualTo(fixture.ticket().getId());
        verify(ticketRepository, never()).save(any());
        verify(activityService).recordActivity(ActivityType.LABEL_REMOVED, "backend", null,
                fixture.ticket(), fixture.actor());
    }

    @Test
    void removingUnattachedLabelIsIdempotentAndRecordsNoActivity() {
        Fixture fixture = newFixture();
        Label label = persistedLabel(UUID.randomUUID(), "backend", fixture.workspace());
        when(labelRepository.findById(label.getId())).thenReturn(Optional.of(label));

        labelService.removeLabelFromTicket(fixture.ticket().getId(), label.getId(), fixture.actor().getId());

        assertThat(fixture.ticket().getLabels()).isEmpty();
        verify(ticketRepository, never()).save(any());
        verify(activityService, never()).recordActivity(any(), any(), any(), any(), any());
    }
}
