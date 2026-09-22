package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TicketRepositoryTest {

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

    private Workspace newWorkspace() {
        return workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
    }

    private User newUser(Workspace workspace, String email) {
        return userRepository.saveAndFlush(new User("Test User", email, "hashed-password", UserRole.MEMBER, workspace));
    }

    private Project newProject(Workspace workspace, String key) {
        return projectRepository.saveAndFlush(new Project("E-Commerce", key, null, workspace));
    }

    @Test
    void ticketIsPersistedReferencingItsProjectAndCreator() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project project = newProject(workspace, "ECOM");

        Ticket saved = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", "Details", TicketStatus.BACKLOG, TicketPriority.HIGH,
                        project, creator, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getProject().getId()).isEqualTo(project.getId());
        assertThat(saved.getCreator().getId()).isEqualTo(creator.getId());
        assertThat(saved.getAssignee()).isNull();
    }

    @Test
    void assigneeMapsCorrectlyWhenPresent() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        User assignee = newUser(workspace, "assignee@example.com");
        Project project = newProject(workspace, "ECOM");

        Ticket saved = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.TODO, TicketPriority.MEDIUM,
                        project, creator, assignee));

        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAssignee()).isNotNull();
        assertThat(reloaded.getAssignee().getId()).isEqualTo(assignee.getId());
    }

    @Test
    void descriptionCanBeNull() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project project = newProject(workspace, "ECOM");

        Ticket saved = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        project, creator, null));

        assertThat(saved.getDescription()).isNull();
    }

    @Test
    void statusAndPriorityArePersistedAsStringRepresentation() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project project = newProject(workspace, "ECOM");

        Ticket saved = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL,
                        project, creator, null));

        String rawStatus = (String) entityManager.getEntityManager()
                .createNativeQuery("SELECT status FROM tickets WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();
        String rawPriority = (String) entityManager.getEntityManager()
                .createNativeQuery("SELECT priority FROM tickets WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();

        assertThat(rawStatus).isEqualTo("IN_PROGRESS");
        assertThat(rawPriority).isEqualTo("CRITICAL");
    }

    @Test
    void findByProjectIdAndTicketNumberReturnsMatchingTicket() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project project = newProject(workspace, "ECOM");
        ticketRepository.saveAndFlush(
                new Ticket(7L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        project, creator, null));

        Optional<Ticket> found = ticketRepository.findByProjectIdAndTicketNumber(project.getId(), 7L);

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Fix checkout bug");
    }

    @Test
    void findByProjectIdAndTicketNumberReturnsEmptyWhenNoMatch() {
        Workspace workspace = newWorkspace();
        Project project = newProject(workspace, "ECOM");

        assertThat(ticketRepository.findByProjectIdAndTicketNumber(project.getId(), 99L)).isEmpty();
    }

    @Test
    void existsByProjectIdAndTicketNumberReflectsPersistedState() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project project = newProject(workspace, "ECOM");
        ticketRepository.saveAndFlush(
                new Ticket(3L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        project, creator, null));

        assertThat(ticketRepository.existsByProjectIdAndTicketNumber(project.getId(), 3L)).isTrue();
        assertThat(ticketRepository.existsByProjectIdAndTicketNumber(project.getId(), 4L)).isFalse();
    }

    @Test
    void duplicateTicketNumberIsRejectedWithinTheSameProject() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project project = newProject(workspace, "ECOM");
        ticketRepository.saveAndFlush(
                new Ticket(1L, "First ticket", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        project, creator, null));

        Ticket duplicate = new Ticket(1L, "Second ticket", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                project, creator, null);

        assertThatThrownBy(() -> ticketRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameTicketNumberIsAllowedAcrossDifferentProjects() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project first = newProject(workspace, "ECOM");
        Project second = newProject(workspace, "MOB");

        ticketRepository.saveAndFlush(
                new Ticket(1L, "First project ticket", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        first, creator, null));
        Ticket saved = ticketRepository.saveAndFlush(
                new Ticket(1L, "Second project ticket", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        second, creator, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(ticketRepository.existsByProjectIdAndTicketNumber(first.getId(), 1L)).isTrue();
        assertThat(ticketRepository.existsByProjectIdAndTicketNumber(second.getId(), 1L)).isTrue();
    }

    @Test
    void displayKeyCombinesProjectKeyAndTicketNumber() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project project = newProject(workspace, "ECOM");

        Ticket saved = ticketRepository.saveAndFlush(
                new Ticket(7L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                        project, creator, null));

        assertThat(saved.getDisplayKey()).isEqualTo("ECOM-7");
    }

    @Test
    void ticketNumberBelowOneIsRejectedByDatabaseConstraint() {
        Workspace workspace = newWorkspace();
        User creator = newUser(workspace, "creator@example.com");
        Project project = newProject(workspace, "ECOM");

        Ticket invalid = new Ticket(0L, "Invalid ticket", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                project, creator, null);

        assertThatThrownBy(() -> ticketRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
