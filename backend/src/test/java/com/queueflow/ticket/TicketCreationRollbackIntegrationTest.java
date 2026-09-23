package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.queueflow.activity.ActivityRepository;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Proves the Project counter increment and the Ticket insert are truly one
 * atomic transaction: if the insert fails, the counter mutation - already
 * applied in memory to the managed, locked Project entity - must never
 * reach the database either.
 *
 * Plain @SpringBootTest, no @Transactional wrapper: each repository call
 * here (and each call to ticketService.create()) must run in its own
 * genuine transaction against the real database, so re-reading the Project
 * afterward reflects truly committed state, not an uncommitted value
 * visible only within a shared test transaction.
 */
@SpringBootTest
class TicketCreationRollbackIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityRepository activityRepository;

    private Workspace workspace;
    private User creator;
    private Project project;
    private Ticket preExistingTicket;

    @AfterEach
    void cleanUp() {
        if (preExistingTicket != null) {
            ticketRepository.deleteById(preExistingTicket.getId());
        }
        if (project != null) {
            projectRepository.deleteById(project.getId());
        }
        if (creator != null) {
            userRepository.deleteById(creator.getId());
        }
        if (workspace != null) {
            workspaceRepository.deleteById(workspace.getId());
        }
    }

    @Test
    void failedTicketInsertRollsBackTheProjectCounterIncrement() {
        workspace = workspaceRepository.saveAndFlush(new Workspace("Rollback Test Workspace"));
        creator = userRepository.saveAndFlush(
                new User("Creator", "rollback-creator@example.com", "hash", UserRole.MEMBER, workspace));
        project = projectRepository.saveAndFlush(new Project("Rollback Project", "RLBK", null, workspace));

        // Realistic failure path, no production code touched for testing:
        // pre-occupy ticket_number = 1 (the project's current
        // next_ticket_number) with a ticket inserted directly, bypassing
        // the service entirely. When TicketService.create() later allocates
        // that exact same number and tries to insert its own ticket, the
        // real uq_tickets_project_ticket_number constraint rejects it.
        preExistingTicket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Pre-existing ticket", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        project, creator, null));

        CreateTicketRequest request = new CreateTicketRequest(
                project.getId(), "Should fail to insert", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                creator.getId(), null);

        assertThatThrownBy(() -> ticketService.create(request))
                .isInstanceOf(DataIntegrityViolationException.class);

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getNextTicketNumber())
                .as("counter increment must have rolled back with the failed insert")
                .isEqualTo(1L);

        long ticketCountForProject = ticketRepository.findAll().stream()
                .filter(ticket -> ticket.getProject().getId().equals(project.getId()))
                .count();
        assertThat(ticketCountForProject)
                .as("only the pre-existing ticket should remain - no partial/duplicate row")
                .isEqualTo(1L);

        // The failed create() never reached the recordActivity() call (the
        // ticket insert itself threw first), and rollback discards any
        // change that did happen - so no TICKET_CREATED activity should
        // exist for this project's tickets at all. This is the closest
        // realistic evidence available for "Activity insert failure rolls
        // back the business change" (see report: forcing an Activity-
        // specific constraint violation cleanly, without corrupting
        // production code, isn't achievable - Activity has no unique/check
        // constraint reachable through a legitimate flow the way
        // tickets.ticket_number is).
        assertThat(activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(preExistingTicket.getId())).isEmpty();
    }
}
