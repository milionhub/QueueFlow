package com.queueflow.comment;

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

import com.queueflow.comment.dto.CommentResponse;
import com.queueflow.comment.dto.CreateCommentRequest;
import com.queueflow.comment.dto.UpdateCommentRequest;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Real-PostgreSQL coverage for what mocks can't prove: actual persistence
 * of ticket_id/author_id, real chronological ordering, real dirty-checked
 * updates (with updatedAt advancing), and that delete only removes the
 * Comment row. Field-presence/authorization branching logic is already
 * exhaustively covered by CommentServiceTest, so this class intentionally
 * does not repeat every case.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(CommentService.class)
class CommentServiceIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

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

    @Test
    void creatingACommentPersistsTicketIdAndAuthorIdCorrectly() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User author = userRepository.saveAndFlush(
                new User("Ada Lovelace", "ada@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, author, null));

        CommentResponse response = commentService.create(
                new CreateCommentRequest(ticket.getId(), author.getId(), "Looking into this now"));
        entityManager.flush();
        entityManager.clear();

        Comment reloaded = commentRepository.findById(response.id()).orElseThrow();
        assertThat(reloaded.getTicket().getId()).isEqualTo(ticket.getId());
        assertThat(reloaded.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(reloaded.getContent()).isEqualTo("Looking into this now");
    }

    @Test
    void getByTicketReturnsChronologicalOrdering() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User author = userRepository.saveAndFlush(
                new User("Ada Lovelace", "ada2@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM2", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, author, null));

        Comment first = commentRepository.saveAndFlush(new Comment("First comment", ticket, author));
        Comment second = commentRepository.saveAndFlush(new Comment("Second comment", ticket, author));

        // Postgres' now() is frozen to this test transaction's start, so
        // both inserts above would otherwise get an identical created_at.
        // Forcing distinct, deterministic timestamps directly avoids
        // relying on Thread.sleep for the ordering assertion below.
        OffsetDateTime now = OffsetDateTime.now();
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE comments SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, now.minusMinutes(10))
                .setParameter(2, first.getId())
                .executeUpdate();
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE comments SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, now.minusMinutes(5))
                .setParameter(2, second.getId())
                .executeUpdate();
        entityManager.clear();

        List<CommentResponse> responses = commentService.getByTicket(ticket.getId());

        assertThat(responses).extracting(CommentResponse::id).containsExactly(first.getId(), second.getId());
    }

    @Test
    void updatingContentPersistsNewValueAndAdvancesUpdatedAtWhileOtherFieldsStayFixed() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User author = userRepository.saveAndFlush(
                new User("Ada Lovelace", "ada3@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM3", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, author, null));
        Comment comment = commentRepository.saveAndFlush(new Comment("Original content", ticket, author));
        OffsetDateTime createdAt = comment.getCreatedAt();
        OffsetDateTime updatedAtBeforeUpdate = comment.getUpdatedAt();

        commentService.update(comment.getId(), author.getId(), new UpdateCommentRequest("Updated content"));
        entityManager.flush();
        entityManager.clear();

        Comment reloaded = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("Updated content");
        // updatedAt is set by Comment's @PreUpdate via OffsetDateTime.now()
        // (real JVM wall-clock time), unlike created_at's DB-side now()
        // which is frozen to this test transaction's start - genuine
        // repository round-trips happen in between, so this is reliably
        // later without needing Thread.sleep.
        assertThat(reloaded.getUpdatedAt()).isAfter(updatedAtBeforeUpdate);
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(reloaded.getTicket().getId()).isEqualTo(ticket.getId());
    }

    @Test
    void deletingRemovesOnlyTheCommentNotTicketOrUser() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User author = userRepository.saveAndFlush(
                new User("Ada Lovelace", "ada4@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM4", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, author, null));
        Comment comment = commentRepository.saveAndFlush(new Comment("To be deleted", ticket, author));

        commentService.delete(comment.getId(), author.getId());
        entityManager.flush();

        assertThat(commentRepository.findById(comment.getId())).isEmpty();
        assertThat(ticketRepository.findById(ticket.getId())).isPresent();
        assertThat(userRepository.findById(author.getId())).isPresent();
    }

    @Test
    void crossWorkspaceCommentCreationIsRejectedAndPersistsNothing() {
        Workspace ticketWorkspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Workspace authorWorkspace = workspaceRepository.saveAndFlush(new Workspace("Globex"));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator5@example.com", "hash", UserRole.MEMBER, ticketWorkspace));
        User outsider = userRepository.saveAndFlush(
                new User("Outsider", "outsider@example.com", "hash", UserRole.MEMBER, authorWorkspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM5", null, ticketWorkspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, creator, null));

        assertThatThrownBy(() -> commentService.create(
                new CreateCommentRequest(ticket.getId(), outsider.getId(), "Should fail")))
                .isInstanceOf(BusinessRuleViolationException.class);
        entityManager.flush();

        assertThat(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())).isEmpty();
    }
}
