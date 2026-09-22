package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.queueflow.activity.ActivityService;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.ticket.dto.UpdateTicketRequest;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Real-PostgreSQL coverage for behavior mocks can't prove: that
 * TicketService.update() actually persists via dirty checking (no explicit
 * save), that omitted/explicit-null PATCH semantics reach the database
 * columns correctly, and that the @PreUpdate-managed updatedAt genuinely
 * advances on a real UPDATE. Field-presence branching logic itself is
 * already exhaustively covered by the Mockito unit tests in
 * TicketServiceTest, so this class intentionally does not repeat every
 * case - only what needs the real persistence layer to prove.
 *
 * Standard @DataJpaTest + Replace.NONE pattern (as in every other
 * repository integration test in this codebase): the whole test method
 * runs in one transaction that rolls back automatically, so no manual
 * cleanup is needed and TicketService.update()'s own @Transactional simply
 * joins that same transaction. @DataJpaTest only auto-configures
 * repository-layer beans, not @Service beans, so TicketService is pulled
 * into the slice explicitly via @Import.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TicketService.class, ActivityService.class})
class TicketUpdateIntegrationTest {

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
    private TestEntityManager entityManager;

    private Ticket persistTicket(Project project, User creator, User assignee, String description) {
        return ticketRepository.saveAndFlush(
                new Ticket(1L, "Original title", description, TicketStatus.BACKLOG, TicketPriority.LOW,
                        project, creator, assignee));
    }

    @Test
    void updateChangesRequestedFieldsAndLeavesOmittedAndImmutableFieldsUnchanged() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator@example.com", "hash", UserRole.MEMBER, workspace));
        User assignee = userRepository.saveAndFlush(
                new User("Assignee", "assignee@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = persistTicket(project, creator, null, "Original description");

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Updated title");
        request.setStatus(TicketStatus.IN_PROGRESS);
        request.setAssigneeId(assignee.getId());
        // description and priority intentionally omitted from the request

        TicketResponse response = ticketService.update(ticket.getId(), creator.getId(), request);
        entityManager.flush();
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Updated title");
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(reloaded.getAssignee().getId()).isEqualTo(assignee.getId());
        assertThat(reloaded.getDescription()).isEqualTo("Original description");
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.LOW);

        assertThat(reloaded.getTicketNumber()).isEqualTo(1L);
        assertThat(reloaded.getProject().getId()).isEqualTo(project.getId());
        assertThat(reloaded.getCreator().getId()).isEqualTo(creator.getId());

        assertThat(response.title()).isEqualTo("Updated title");
        assertThat(response.assigneeId()).isEqualTo(assignee.getId());
    }

    @Test
    void explicitNullDescriptionClearsTheColumn() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator2@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM2", null, workspace));
        Ticket ticket = persistTicket(project, creator, null, "Has a description");

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setDescription(null);

        ticketService.update(ticket.getId(), creator.getId(), request);
        entityManager.flush();
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isNull();
    }

    @Test
    void explicitNullAssigneeClearsAssigneeId() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator3@example.com", "hash", UserRole.MEMBER, workspace));
        User assignee = userRepository.saveAndFlush(
                new User("Assignee", "assignee3@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM3", null, workspace));
        Ticket ticket = persistTicket(project, creator, assignee, null);

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssigneeId(null);

        ticketService.update(ticket.getId(), creator.getId(), request);
        entityManager.flush();
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getAssignee()).isNull();
    }

    @Test
    void updatedAtAdvancesAfterARealUpdateWhileCreatedAtStaysFixed() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator4@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM4", null, workspace));
        Ticket ticket = persistTicket(project, creator, null, null);
        OffsetDateTime createdAt = ticket.getCreatedAt();
        OffsetDateTime updatedAtBeforeUpdate = ticket.getUpdatedAt();

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Updated title");

        ticketService.update(ticket.getId(), creator.getId(), request);
        entityManager.flush();
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        // updatedAt is set by Ticket's @PreUpdate via OffsetDateTime.now()
        // (real JVM wall-clock time), unlike created_at's DB-side now()
        // which is frozen to this test transaction's start. Genuine
        // repository round-trips happen between persisting the ticket and
        // calling update(), so updatedAt is reliably later - no
        // Thread.sleep needed.
        assertThat(reloaded.getUpdatedAt()).isAfter(updatedAtBeforeUpdate);
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
    }
}
