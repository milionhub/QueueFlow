package com.queueflow.comment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

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

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CommentRepositoryTest {

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

    /**
     * Postgres' now() returns the transaction start time, not per-statement
     * wall-clock time, so two inserts in the same test transaction would
     * otherwise get an identical created_at. Setting it explicitly via a
     * native update gives deterministic ordering without Thread.sleep.
     */
    private void forceCreatedAt(String table, java.util.UUID id, OffsetDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE " + table + " SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, id)
                .executeUpdate();
    }

    @Test
    void commentIsPersistedReferencingTicketAndAuthor() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User author = userRepository.saveAndFlush(new User("Author", "author@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, author, null));

        Comment saved = commentRepository.saveAndFlush(new Comment("Looking into this now", ticket, author));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getContent()).isEqualTo("Looking into this now");
        assertThat(saved.getTicket().getId()).isEqualTo(ticket.getId());
        assertThat(saved.getAuthor().getId()).isEqualTo(author.getId());
    }

    @Test
    void findByTicketIdOrderByCreatedAtAscReturnsOldestFirst() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User author = userRepository.saveAndFlush(new User("Author", "author@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, author, null));

        Comment first = commentRepository.saveAndFlush(new Comment("First comment", ticket, author));
        Comment second = commentRepository.saveAndFlush(new Comment("Second comment", ticket, author));

        OffsetDateTime now = OffsetDateTime.now();
        forceCreatedAt("comments", first.getId(), now.minusMinutes(10));
        forceCreatedAt("comments", second.getId(), now.minusMinutes(5));
        entityManager.clear();

        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        assertThat(comments).extracting(Comment::getId).containsExactly(first.getId(), second.getId());
    }
}
