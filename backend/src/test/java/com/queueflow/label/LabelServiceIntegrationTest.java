package com.queueflow.label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.queueflow.activity.Activity;
import com.queueflow.activity.ActivityRepository;
import com.queueflow.activity.ActivityService;
import com.queueflow.activity.ActivityType;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
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
 * Real-PostgreSQL coverage for what mocks can't prove: persistence of
 * Label, the ticket_labels join-table row lifecycle through
 * LabelService (which never calls ticketRepository.save()), and the actual
 * observed effect (or lack of it) on Ticket.updatedAt.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({LabelService.class, ActivityService.class})
class LabelServiceIntegrationTest {

    @Autowired
    private LabelService labelService;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private long ticketLabelRowCount(Object ticketId, Object labelId) {
        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM ticket_labels WHERE ticket_id = ?1 AND label_id = ?2")
                .setParameter(1, ticketId)
                .setParameter(2, labelId)
                .getSingleResult();
        return count.longValue();
    }

    @Test
    void labelPersistsWithDatabaseGeneratedUuidAndTimestamps() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        LabelResponse response = labelService.create(new CreateLabelRequest(workspace.getId(), "backend"));

        assertThat(response.id()).isNotNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.workspaceId()).isEqualTo(workspace.getId());
    }

    @Test
    void labelNameUniquenessRemainsScopedToWorkspace() {
        Workspace first = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Workspace second = workspaceRepository.saveAndFlush(new Workspace("Globex"));

        labelService.create(new CreateLabelRequest(first.getId(), "backend"));
        // Same name, different workspace: must succeed.
        LabelResponse secondResponse = labelService.create(new CreateLabelRequest(second.getId(), "backend"));

        assertThat(secondResponse.id()).isNotNull();
        assertThat(labelRepository.existsByWorkspaceIdAndName(first.getId(), "backend")).isTrue();
        assertThat(labelRepository.existsByWorkspaceIdAndName(second.getId(), "backend")).isTrue();
    }

    @Test
    void attachingLabelCreatesExactlyOneTicketLabelsRowAndReattachingDoesNotDuplicateIt() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));
        Label label = labelRepository.saveAndFlush(new Label("backend", workspace));

        labelService.addLabelToTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();

        assertThat(ticketLabelRowCount(ticket.getId(), label.getId())).isEqualTo(1L);

        // Re-attaching the same label must not create a duplicate row, nor
        // a second LABEL_ADDED activity.
        TicketResponse response = labelService.addLabelToTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();

        assertThat(ticketLabelRowCount(ticket.getId(), label.getId())).isEqualTo(1L);
        assertThat(response.id()).isEqualTo(ticket.getId());

        List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        assertThat(activities).extracting(Activity::getType).containsExactly(ActivityType.LABEL_ADDED);
    }

    @Test
    void removingLabelDeletesTheRowAndRemovingAgainIsHarmless() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator2@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM2", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));
        Label label = labelRepository.saveAndFlush(new Label("backend", workspace));

        labelService.addLabelToTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        assertThat(ticketLabelRowCount(ticket.getId(), label.getId())).isEqualTo(1L);

        labelService.removeLabelFromTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        assertThat(ticketLabelRowCount(ticket.getId(), label.getId())).isEqualTo(0L);

        // Removing again: no error, still zero rows, and no second
        // LABEL_REMOVED activity.
        labelService.removeLabelFromTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        assertThat(ticketLabelRowCount(ticket.getId(), label.getId())).isEqualTo(0L);

        // Neither the Ticket nor the Label was deleted by the association removal.
        assertThat(ticketRepository.findById(ticket.getId())).isPresent();
        assertThat(labelRepository.findById(label.getId())).isPresent();

        List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        assertThat(activities).extracting(Activity::getType)
                .containsExactly(ActivityType.LABEL_ADDED, ActivityType.LABEL_REMOVED);
    }

    @Test
    void crossWorkspaceAssociationIsRejectedAndCreatesNoJoinTableRow() {
        Workspace ticketWorkspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Workspace labelWorkspace = workspaceRepository.saveAndFlush(new Workspace("Globex"));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator3@example.com", "hash", UserRole.MEMBER, ticketWorkspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM3", null, ticketWorkspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));
        Label label = labelRepository.saveAndFlush(new Label("backend", labelWorkspace));

        assertThatThrownBy(() -> labelService.addLabelToTicket(ticket.getId(), label.getId(), creator.getId()))
                .isInstanceOf(BusinessRuleViolationException.class);
        entityManager.flush();

        assertThat(ticketLabelRowCount(ticket.getId(), label.getId())).isEqualTo(0L);
    }

    @Test
    void addingALabelThroughLabelServiceDoesNotAdvanceTicketUpdatedAt() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator4@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM4", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));
        Label label = labelRepository.saveAndFlush(new Label("backend", workspace));
        OffsetDateTime updatedAtBefore = ticket.getUpdatedAt();

        labelService.addLabelToTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        entityManager.clear();

        // Observed behavior (see report): a pure many-to-many join-table
        // change is persisted through its own collection-table SQL, fully
        // independent of the owning Ticket row's own UPDATE statement.
        // Ticket.@PreUpdate only fires when Hibernate is about to issue an
        // UPDATE for the tickets row itself, which a label-only change does
        // not trigger - so updatedAt is NOT expected to advance here.
        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAtBefore);
    }
}
