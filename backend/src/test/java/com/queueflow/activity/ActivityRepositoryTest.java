package com.queueflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
class ActivityRepositoryTest {

    @Autowired
    private ActivityRepository activityRepository;

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

    private void forceCreatedAt(UUID id, OffsetDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE activities SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, id)
                .executeUpdate();
    }

    @Test
    void activityIsPersistedReferencingTicketAndUser() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User user = userRepository.saveAndFlush(new User("Author", "author@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, user, null));

        Activity saved = activityRepository.saveAndFlush(
                new Activity(ActivityType.TICKET_CREATED, null, null, ticket, user));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getTicket().getId()).isEqualTo(ticket.getId());
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getOldValue()).isNull();
        assertThat(saved.getNewValue()).isNull();
    }

    @Test
    void oldAndNewValueArePersistedWhenPresent() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User user = userRepository.saveAndFlush(new User("Author", "author@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, user, null));

        Activity saved = activityRepository.saveAndFlush(
                new Activity(ActivityType.STATUS_CHANGED, "BACKLOG", "TODO", ticket, user));

        assertThat(saved.getOldValue()).isEqualTo("BACKLOG");
        assertThat(saved.getNewValue()).isEqualTo("TODO");
    }

    @Test
    void activityTypeIsPersistedAsStringRepresentation() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User user = userRepository.saveAndFlush(new User("Author", "author@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, user, null));

        Activity saved = activityRepository.saveAndFlush(
                new Activity(ActivityType.LABEL_ADDED, null, "bug", ticket, user));

        String rawType = (String) entityManager.getEntityManager()
                .createNativeQuery("SELECT type FROM activities WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();

        assertThat(rawType).isEqualTo("LABEL_ADDED");
    }

    @Test
    void findByTicketIdOrderByCreatedAtAscReturnsOldestFirst() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User user = userRepository.saveAndFlush(new User("Author", "author@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, user, null));

        Activity first = activityRepository.saveAndFlush(
                new Activity(ActivityType.TICKET_CREATED, null, null, ticket, user));
        Activity second = activityRepository.saveAndFlush(
                new Activity(ActivityType.STATUS_CHANGED, "BACKLOG", "TODO", ticket, user));

        OffsetDateTime now = OffsetDateTime.now();
        forceCreatedAt(first.getId(), now.minusMinutes(10));
        forceCreatedAt(second.getId(), now.minusMinutes(5));
        entityManager.clear();

        List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        assertThat(activities).extracting(Activity::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void activityHasNoUpdatedAtPersistenceField() {
        assertThatThrownBy(() -> Activity.class.getDeclaredField("updatedAt"))
                .isInstanceOf(NoSuchFieldException.class);
    }
}
