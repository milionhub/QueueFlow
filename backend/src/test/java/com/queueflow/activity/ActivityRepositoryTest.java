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
    void historyQueryReturnsOldestFirst() {
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

        List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());

        assertThat(activities).extracting(Activity::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void historyQueryBreaksCreatedAtTiesByIdAscendingDeterministically() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User user = userRepository.saveAndFlush(new User("Author", "author@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket(1L, "Fix checkout bug", null, TicketStatus.BACKLOG, TicketPriority.LOW, project, user, null));

        Activity a1 = activityRepository.saveAndFlush(
                new Activity(ActivityType.TICKET_CREATED, null, null, ticket, user));
        Activity a2 = activityRepository.saveAndFlush(
                new Activity(ActivityType.TITLE_CHANGED, "Old", "New", ticket, user));
        Activity a3 = activityRepository.saveAndFlush(
                new Activity(ActivityType.DESCRIPTION_CHANGED, null, "Details", ticket, user));
        Activity a4 = activityRepository.saveAndFlush(
                new Activity(ActivityType.STATUS_CHANGED, "BACKLOG", "TODO", ticket, user));

        // Precondition: uuidv7 ids ascend in insertion order. Compared as
        // lowercase hex strings, which matches PostgreSQL's uuid byte order
        // (java.util.UUID.compareTo uses signed longs and does not).
        assertThat(List.of(a1, a2, a3, a4)).extracting(a -> a.getId().toString()).isSorted();

        // a2..a4 share one identical created_at - exactly what a multi-field
        // PATCH produces via transaction-stable now(). They are forced in
        // REVERSE id order: each UPDATE writes a new row version, so heap
        // and (ticket_id, created_at) index order become a4, a3, a2 - a
        // query without the id tie-breaker would tend to return that
        // reversed order. a1 has the SMALLEST id but the LATEST created_at,
        // proving createdAt stays the primary criterion and id only breaks
        // ties.
        OffsetDateTime tie = OffsetDateTime.parse("2026-09-23T10:00:00Z");
        forceCreatedAt(a4.getId(), tie);
        forceCreatedAt(a3.getId(), tie);
        forceCreatedAt(a2.getId(), tie);
        forceCreatedAt(a1.getId(), tie.plusMinutes(5));

        for (int attempt = 0; attempt < 3; attempt++) {
            entityManager.clear();
            List<Activity> activities = activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
            assertThat(activities).extracting(Activity::getId)
                    .as("retrieval #%d", attempt + 1)
                    .containsExactly(a2.getId(), a3.getId(), a4.getId(), a1.getId());
        }
    }

    @Test
    void activityHasNoUpdatedAtPersistenceField() {
        assertThatThrownBy(() -> Activity.class.getDeclaredField("updatedAt"))
                .isInstanceOf(NoSuchFieldException.class);
    }
}
