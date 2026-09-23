package com.queueflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.queueflow.comment.Comment;
import com.queueflow.comment.CommentRepository;
import com.queueflow.label.Label;
import com.queueflow.label.LabelRepository;
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
 * Feeds the classifier the exceptions real PostgreSQL + Hibernate + Spring
 * Data actually produce. All four violations arrive as the same
 * DataIntegrityViolationException -> Hibernate ConstraintViolationException
 * -> PSQLException chain; only the unique violation may be classified as a
 * client conflict.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class DatabaseConflictsPostgresTest {

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Workspace workspace;
    private User user;
    private Project project;

    @BeforeEach
    void setUp() {
        workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        user = userRepository.saveAndFlush(new User("User", "conflicts@example.com", "hash", UserRole.MEMBER, workspace));
        project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
    }

    private static DataIntegrityViolationException integrityFailure(Runnable action) {
        Throwable thrown = catchThrowable(action::run);
        assertThat(thrown).isExactlyInstanceOf(DataIntegrityViolationException.class);
        return (DataIntegrityViolationException) thrown;
    }

    @Test
    void realUniqueViolationIsAConflict() {
        labelRepository.saveAndFlush(new Label("bug", workspace));

        DataIntegrityViolationException exception =
                integrityFailure(() -> labelRepository.saveAndFlush(new Label("bug", workspace)));

        assertThat(DatabaseConflicts.isUniqueViolation(exception)).isTrue();
    }

    @Test
    void realForeignKeyViolationIsNotAConflict() {
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "T", null, TicketStatus.TODO, TicketPriority.LOW, project, user, null));
        User missingAuthor = entityManager.getEntityManager().getReference(User.class, UUID.randomUUID());

        DataIntegrityViolationException exception =
                integrityFailure(() -> commentRepository.saveAndFlush(new Comment("c", ticket, missingAuthor)));

        assertThat(DatabaseConflicts.isUniqueViolation(exception)).isFalse();
    }

    @Test
    void realCheckViolationIsNotAConflict() {
        DataIntegrityViolationException exception = integrityFailure(() -> ticketRepository.saveAndFlush(
                new Ticket(0L, "T", null, TicketStatus.TODO, TicketPriority.LOW, project, user, null)));

        assertThat(DatabaseConflicts.isUniqueViolation(exception)).isFalse();
    }

    @Test
    void realNotNullViolationIsNotAConflict() {
        DataIntegrityViolationException exception =
                integrityFailure(() -> labelRepository.saveAndFlush(new Label(null, workspace)));

        assertThat(DatabaseConflicts.isUniqueViolation(exception)).isFalse();
    }
}
