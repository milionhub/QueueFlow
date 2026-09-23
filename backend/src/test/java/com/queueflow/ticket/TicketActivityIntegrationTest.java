package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import com.queueflow.label.Label;
import com.queueflow.label.LabelRepository;
import com.queueflow.label.LabelService;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.ticket.dto.UpdateTicketRequest;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Real-PostgreSQL coverage proving Activity records are created correctly
 * and atomically alongside the business operations that cause them
 * (Phase 1.6F requirements A/B/C). Requirement D (atomicity under an
 * Activity-insert failure specifically) is addressed in the report rather
 * than with a dedicated test here: Activity has no unique/check constraint
 * reachable through any legitimate service call (unlike
 * tickets.ticket_number, which TicketCreationRollbackIntegrationTest
 * already exploits for exactly this kind of proof), so forcing an
 * Activity-specific failure cleanly would require corrupting production
 * entity state through a test-only hook rather than a realistic path. That
 * existing rollback test was extended to additionally assert zero
 * Activity rows exist after the failure, which is the closest realistic
 * evidence available; the transaction structure itself (recordActivity()
 * using plain @Transactional / default REQUIRED propagation, never
 * REQUIRES_NEW) guarantees the same rollback behavior would apply if an
 * Activity insert ever did fail.
 *
 * Standard @DataJpaTest + Replace.NONE pattern: one transaction per test
 * method, rolled back automatically, so no manual cleanup is needed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TicketService.class, LabelService.class, ActivityService.class})
class TicketActivityIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private LabelService labelService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private TestEntityManager entityManager;

    // ---------------------------------------------------------------
    // A) Ticket creation
    // ---------------------------------------------------------------

    @Test
    void creatingATicketCommitsProjectCounterTicketAndActivityTogether() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));

        TicketResponse response = ticketService.create(new CreateTicketRequest(
                project.getId(), "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                creator.getId(), null));
        entityManager.flush();
        entityManager.clear();

        List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(response.id());
        assertThat(activities).hasSize(1);
        Activity activity = activities.get(0);
        assertThat(activity.getType()).isEqualTo(ActivityType.TICKET_CREATED);
        assertThat(activity.getOldValue()).isNull();
        assertThat(activity.getNewValue()).isNull();
        assertThat(activity.getUser().getId()).isEqualTo(creator.getId());
        assertThat(activity.getTicket().getId()).isEqualTo(response.id());

        Project reloadedProject = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloadedProject.getNextTicketNumber()).isEqualTo(2L);
        assertThat(ticketRepository.findById(response.id())).isPresent();
    }

    // ---------------------------------------------------------------
    // B) Ticket update
    // ---------------------------------------------------------------

    @Test
    void updatingMultipleFieldsCreatesExpectedActivitiesInChronologicalOrderWithCorrectValues() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator2@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM2", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(new Ticket(1L, "Original title", "Original description",
                TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Updated title");
        request.setStatus(TicketStatus.IN_PROGRESS);
        // priority and description intentionally left unset -> no activity for those

        ticketService.update(ticket.getId(), creator.getId(), request);
        entityManager.flush();
        entityManager.clear();

        List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        assertThat(activities).extracting(Activity::getType, Activity::getOldValue, Activity::getNewValue)
                .containsExactly(
                        tuple(ActivityType.TITLE_CHANGED, "Original title", "Updated title"),
                        tuple(ActivityType.STATUS_CHANGED, "BACKLOG", "IN_PROGRESS"));
    }

    @Test
    void updatingWithNoActualChangesCreatesNoActivity() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator3@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM3", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(new Ticket(1L, "Same title", null,
                TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Same title");
        request.setStatus(TicketStatus.BACKLOG);

        ticketService.update(ticket.getId(), creator.getId(), request);
        entityManager.flush();

        assertThat(activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // C) Label
    // ---------------------------------------------------------------

    @Test
    void addingALabelCreatesLabelAddedAndDuplicateAddCreatesNoDuplicateActivity() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator4@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM4", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(new Ticket(1L, "Fix bug", null,
                TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));
        Label label = labelRepository.saveAndFlush(new Label("backend", workspace));

        labelService.addLabelToTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        // Duplicate add: association already present, must not add another activity.
        labelService.addLabelToTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        entityManager.clear();

        List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).getType()).isEqualTo(ActivityType.LABEL_ADDED);
        assertThat(activities.get(0).getOldValue()).isNull();
        assertThat(activities.get(0).getNewValue()).isEqualTo("backend");
    }

    @Test
    void removingALabelCreatesLabelRemovedAndRemovingAgainCreatesNoActivity() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator5@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM5", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(new Ticket(1L, "Fix bug", null,
                TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));
        Label label = labelRepository.saveAndFlush(new Label("backend", workspace));

        labelService.addLabelToTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        labelService.removeLabelFromTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        // Removing again: already absent, must not add another activity.
        labelService.removeLabelFromTicket(ticket.getId(), label.getId(), creator.getId());
        entityManager.flush();
        entityManager.clear();

        List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        assertThat(activities).extracting(Activity::getType, Activity::getOldValue, Activity::getNewValue)
                .containsExactly(
                        tuple(ActivityType.LABEL_ADDED, null, "backend"),
                        tuple(ActivityType.LABEL_REMOVED, "backend", null));
    }
}
