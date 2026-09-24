package com.queueflow.dashboard;

import static com.queueflow.security.TestActors.actorOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.dashboard.dto.DashboardProjectResponse;
import com.queueflow.dashboard.dto.DashboardResponse;
import com.queueflow.dashboard.dto.DashboardStatusCountResponse;
import com.queueflow.dashboard.dto.DashboardTicketResponse;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

import jakarta.persistence.EntityManagerFactory;

/**
 * The dashboard's rules against real PostgreSQL: counting, the open/DONE
 * rule, list order and limits, and tenant isolation. Every test runs next
 * to a fully populated foreign workspace (Bruno's), which also holds a
 * ticket assigned to Ana herself - a cross-tenant row the API can never
 * create, inserted here directly - so any query missing its workspace
 * constraint shows up as a wrong number. updated_at is set explicitly:
 * inside one test transaction every insert would share PostgreSQL's now().
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(DashboardService.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DashboardServiceIntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T12:00:00Z");

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private final Map<UUID, Long> nextNumbers = new HashMap<>();

    private Workspace own;
    private User ana;
    private User pedro;
    private AuthenticatedUser anaActor;

    @BeforeEach
    void createWorkspaces() {
        own = workspaceRepository.saveAndFlush(new Workspace("Acme"));
        ana = user(own, "Ana", UserRole.ADMIN);
        pedro = user(own, "Pedro", UserRole.MEMBER);
        anaActor = actorOf(ana);

        Workspace foreign = workspaceRepository.saveAndFlush(new Workspace("Other"));
        User bruno = user(foreign, "Bruno", UserRole.ADMIN);
        // Same key as a project of Ana's workspace in some tests.
        Project foreignProject = project(foreign, "Beta", "BETA");
        for (TicketStatus status : TicketStatus.values()) {
            ticket(foreignProject, status, TicketPriority.CRITICAL, null, T0.plusDays(30));
            ticket(foreignProject, status, TicketPriority.HIGH, bruno, T0.plusDays(31));
        }
        // Impossible through the API; only the workspace constraint keeps it out.
        ticket(foreignProject, TicketStatus.TODO, TicketPriority.CRITICAL, ana, T0.plusDays(40));
    }

    // ------------------------------------------------------------------
    // empty workspace
    // ------------------------------------------------------------------

    @Test
    void emptyWorkspaceHasAllFiveStatusesAtZeroAndEmptyLists() {
        DashboardResponse dashboard = dashboard(anaActor);

        assertThat(dashboard.statusCounts()).extracting(DashboardStatusCountResponse::status)
                .containsExactly(TicketStatus.BACKLOG, TicketStatus.TODO, TicketStatus.IN_PROGRESS,
                        TicketStatus.REVIEW, TicketStatus.DONE);
        assertThat(dashboard.statusCounts()).extracting(DashboardStatusCountResponse::count)
                .containsOnly(0L);
        assertThat(dashboard.unassignedOpenCount()).isZero();
        assertThat(dashboard.projects()).isEmpty();
        assertThat(dashboard.assignedToMe().openCount()).isZero();
        assertThat(dashboard.assignedToMe().tickets()).isEmpty();
        assertThat(dashboard.recentlyUpdated()).isEmpty();
    }

    // ------------------------------------------------------------------
    // project summaries
    // ------------------------------------------------------------------

    @Test
    void everyProjectAppearsWithItsCountsInCaseInsensitiveNameThenIdOrder() {
        Project beta = project(own, "Beta", "BETA");
        Project alpha = project(own, "alpha", "ALPHA");
        Project sharedFirst = project(own, "Shared", "SHA");
        Project sharedSecond = project(own, "Shared", "SHB");
        assertThat(List.of(sharedFirst, sharedSecond)).extracting(p -> p.getId().toString()).isSorted();
        ticket(beta, TicketStatus.TODO, TicketPriority.LOW, null, T0);
        ticket(beta, TicketStatus.IN_PROGRESS, TicketPriority.LOW, pedro, T0);
        ticket(beta, TicketStatus.DONE, TicketPriority.LOW, null, T0);
        ticket(beta, TicketStatus.DONE, TicketPriority.LOW, ana, T0);
        ticket(sharedSecond, TicketStatus.BACKLOG, TicketPriority.LOW, null, T0);

        List<DashboardProjectResponse> projects = dashboard(anaActor).projects();

        // LOWER(name) puts "alpha" before "Beta" (plain name order would not
        // in this database's collation); equal names fall back to id.
        assertThat(projects).containsExactly(
                new DashboardProjectResponse(alpha.getId(), "ALPHA", "alpha", 0, 0),
                new DashboardProjectResponse(beta.getId(), "BETA", "Beta", 2, 4),
                new DashboardProjectResponse(sharedFirst.getId(), "SHA", "Shared", 0, 0),
                new DashboardProjectResponse(sharedSecond.getId(), "SHB", "Shared", 1, 1));
    }

    // ------------------------------------------------------------------
    // status counts and the open rule
    // ------------------------------------------------------------------

    @Test
    void doneCountsAsATicketButNeverAsOpenWork() {
        Project project = project(own, "Core", "CORE");
        ticket(project, TicketStatus.BACKLOG, TicketPriority.LOW, null, T0.plusMinutes(1));
        ticket(project, TicketStatus.TODO, TicketPriority.LOW, null, T0.plusMinutes(2));
        Ticket anasTodo = ticket(project, TicketStatus.TODO, TicketPriority.LOW, ana, T0.plusMinutes(3));
        ticket(project, TicketStatus.IN_PROGRESS, TicketPriority.LOW, pedro, T0.plusMinutes(4));
        Ticket anasReview = ticket(project, TicketStatus.REVIEW, TicketPriority.LOW, ana, T0.plusMinutes(5));
        Ticket anasDone = ticket(project, TicketStatus.DONE, TicketPriority.CRITICAL, ana, T0.plusMinutes(6));
        Ticket unassignedDone = ticket(project, TicketStatus.DONE, TicketPriority.LOW, null, T0.plusMinutes(7));
        ticket(project, TicketStatus.DONE, TicketPriority.LOW, pedro, T0.plusMinutes(8));

        DashboardResponse dashboard = dashboard(anaActor);

        assertThat(dashboard.statusCounts()).containsExactly(
                new DashboardStatusCountResponse(TicketStatus.BACKLOG, 1),
                new DashboardStatusCountResponse(TicketStatus.TODO, 2),
                new DashboardStatusCountResponse(TicketStatus.IN_PROGRESS, 1),
                new DashboardStatusCountResponse(TicketStatus.REVIEW, 1),
                new DashboardStatusCountResponse(TicketStatus.DONE, 3));
        // BACKLOG + unassigned TODO; the unassigned DONE ticket is not open.
        assertThat(dashboard.unassignedOpenCount()).isEqualTo(2);
        assertThat(dashboard.projects()).singleElement()
                .isEqualTo(new DashboardProjectResponse(project.getId(), "CORE", "Core", 5, 8));
        assertThat(dashboard.assignedToMe().openCount()).isEqualTo(2);
        assertThat(dashboard.assignedToMe().tickets()).extracting(DashboardTicketResponse::id)
                .containsExactly(anasReview.getId(), anasTodo.getId());
        // DONE tickets are still recent changes.
        assertThat(dashboard.recentlyUpdated()).extracting(DashboardTicketResponse::id)
                .contains(anasDone.getId(), unassignedDone.getId());
    }

    // ------------------------------------------------------------------
    // assigned to me
    // ------------------------------------------------------------------

    @Test
    void assignedToMeListsTheTenMostUrgentOpenTicketsAndCountsThemAll() {
        Project project = project(own, "Core", "CORE");
        Ticket c1 = ticket(project, TicketStatus.TODO, TicketPriority.CRITICAL, ana, T0.plusMinutes(1));
        Ticket c2 = ticket(project, TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL, ana, T0.plusMinutes(5));
        Ticket h1 = ticket(project, TicketStatus.REVIEW, TicketPriority.HIGH, ana, T0.plusMinutes(9));
        Ticket h2 = ticket(project, TicketStatus.BACKLOG, TicketPriority.HIGH, ana, T0.plusMinutes(3));
        Ticket h3 = ticket(project, TicketStatus.TODO, TicketPriority.HIGH, ana, T0.plusMinutes(3));
        Ticket m1 = ticket(project, TicketStatus.TODO, TicketPriority.MEDIUM, ana, T0.plusMinutes(20));
        Ticket m2 = ticket(project, TicketStatus.TODO, TicketPriority.MEDIUM, ana, T0.plusMinutes(2));
        Ticket m3 = ticket(project, TicketStatus.TODO, TicketPriority.MEDIUM, ana, T0.plusMinutes(8));
        Ticket l1 = ticket(project, TicketStatus.TODO, TicketPriority.LOW, ana, T0.plusMinutes(30));
        Ticket l2 = ticket(project, TicketStatus.TODO, TicketPriority.LOW, ana, T0.plusMinutes(4));
        Ticket l3 = ticket(project, TicketStatus.TODO, TicketPriority.LOW, ana, T0.plusMinutes(6));
        Ticket l4 = ticket(project, TicketStatus.TODO, TicketPriority.LOW, ana, T0.plusMinutes(7));
        // Not Ana's open work: DONE, someone else's, unassigned.
        ticket(project, TicketStatus.DONE, TicketPriority.CRITICAL, ana, T0.plusMinutes(50));
        ticket(project, TicketStatus.TODO, TicketPriority.CRITICAL, pedro, T0.plusMinutes(51));
        ticket(project, TicketStatus.TODO, TicketPriority.CRITICAL, null, T0.plusMinutes(52));
        // h2 and h3 share updatedAt: the later-created (larger uuidv7) comes first.
        assertThat(List.of(h2, h3)).extracting(t -> t.getId().toString()).isSorted();

        DashboardResponse dashboard = dashboard(anaActor);

        assertThat(dashboard.assignedToMe().openCount()).isEqualTo(12);
        assertThat(dashboard.assignedToMe().tickets()).extracting(DashboardTicketResponse::id)
                .containsExactly(c2.getId(), c1.getId(), h1.getId(), h3.getId(), h2.getId(),
                        m1.getId(), m3.getId(), m2.getId(), l1.getId(), l4.getId());
        assertThat(dashboard.assignedToMe().tickets()).extracting(DashboardTicketResponse::id)
                .doesNotContain(l3.getId(), l2.getId());

        DashboardTicketResponse first = dashboard.assignedToMe().tickets().getFirst();
        assertThat(first).isEqualTo(new DashboardTicketResponse(c2.getId(), project.getId(),
                "CORE-" + c2.getTicketNumber(), c2.getTitle(), TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL,
                ana.getId(), "Ana", first.updatedAt()));
        assertThat(first.updatedAt()).isEqualTo(T0.plusMinutes(5));
    }

    @Test
    void assignedToMeIsPerActorWithinTheSameDashboard() {
        Project project = project(own, "Core", "CORE");
        Ticket pedros = ticket(project, TicketStatus.TODO, TicketPriority.LOW, pedro, T0);
        ticket(project, TicketStatus.TODO, TicketPriority.LOW, ana, T0);

        DashboardResponse forPedro = dashboard(actorOf(pedro));
        DashboardResponse forAna = dashboard(anaActor);

        assertThat(forPedro.assignedToMe().openCount()).isEqualTo(1);
        assertThat(forPedro.assignedToMe().tickets()).extracting(DashboardTicketResponse::id)
                .containsExactly(pedros.getId());
        // Everything else is the same workspace view for both roles.
        assertThat(forPedro.statusCounts()).isEqualTo(forAna.statusCounts());
        assertThat(forPedro.projects()).isEqualTo(forAna.projects());
        assertThat(forPedro.recentlyUpdated()).isEqualTo(forAna.recentlyUpdated());
        assertThat(forPedro.unassignedOpenCount()).isEqualTo(forAna.unassignedOpenCount());
    }

    // ------------------------------------------------------------------
    // recently updated
    // ------------------------------------------------------------------

    @Test
    void recentlyUpdatedListsTheEightLatestTicketsOfTheWorkspaceIncludingDone() {
        Project core = project(own, "Core", "CORE");
        Project web = project(own, "Web", "WEB");
        Ticket t1 = ticket(core, TicketStatus.TODO, TicketPriority.LOW, null, T0.plusMinutes(1));
        Ticket t2 = ticket(web, TicketStatus.DONE, TicketPriority.LOW, null, T0.plusMinutes(2));
        Ticket t3 = ticket(core, TicketStatus.REVIEW, TicketPriority.HIGH, pedro, T0.plusMinutes(3));
        Ticket t4 = ticket(web, TicketStatus.BACKLOG, TicketPriority.LOW, null, T0.plusMinutes(4));
        Ticket t5 = ticket(core, TicketStatus.DONE, TicketPriority.LOW, ana, T0.plusMinutes(5));
        Ticket t6 = ticket(web, TicketStatus.TODO, TicketPriority.LOW, null, T0.plusMinutes(6));
        Ticket t7 = ticket(core, TicketStatus.TODO, TicketPriority.LOW, null, T0.plusMinutes(7));
        Ticket tieFirstCreated = ticket(web, TicketStatus.TODO, TicketPriority.LOW, null, T0.plusMinutes(9));
        Ticket tieLaterCreated = ticket(core, TicketStatus.TODO, TicketPriority.LOW, null, T0.plusMinutes(9));
        Ticket newest = ticket(web, TicketStatus.IN_PROGRESS, TicketPriority.LOW, null, T0.plusMinutes(10));
        assertThat(List.of(tieFirstCreated, tieLaterCreated)).extracting(t -> t.getId().toString()).isSorted();

        List<DashboardTicketResponse> recent = dashboard(anaActor).recentlyUpdated();

        assertThat(recent).extracting(DashboardTicketResponse::id).containsExactly(newest.getId(),
                tieLaterCreated.getId(), tieFirstCreated.getId(), t7.getId(), t6.getId(), t5.getId(), t4.getId(),
                t3.getId());
        assertThat(recent).extracting(DashboardTicketResponse::id).doesNotContain(t2.getId(), t1.getId());

        DashboardTicketResponse assigned = recent.get(7);
        assertThat(assigned.assigneeId()).isEqualTo(pedro.getId());
        assertThat(assigned.assigneeName()).isEqualTo("Pedro");
        assertThat(assigned.displayKey()).isEqualTo("CORE-" + t3.getTicketNumber());
        assertThat(assigned.projectId()).isEqualTo(core.getId());
        assertThat(assigned.status()).isEqualTo(TicketStatus.REVIEW);
        assertThat(assigned.priority()).isEqualTo(TicketPriority.HIGH);

        DashboardTicketResponse unassigned = recent.getFirst();
        assertThat(unassigned.assigneeId()).isNull();
        assertThat(unassigned.assigneeName()).isNull();
        assertThat(unassigned.displayKey()).isEqualTo("WEB-" + newest.getTicketNumber());
        assertThat(unassigned.updatedAt()).isEqualTo(T0.plusMinutes(10));
    }

    // ------------------------------------------------------------------
    // access and efficiency
    // ------------------------------------------------------------------

    @Test
    void anotherOrUnknownWorkspaceIsNotFoundBeforeAnyQuery() {
        UUID foreignId = workspaceRepository.saveAndFlush(new Workspace("Third")).getId();
        Statistics statistics = statistics();
        statistics.clear();

        assertThatThrownBy(() -> dashboardService.getByWorkspace(anaActor, foreignId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Workspace not found: " + foreignId);
        UUID unknownId = UUID.randomUUID();
        assertThatThrownBy(() -> dashboardService.getByWorkspace(anaActor, unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Workspace not found: " + unknownId);
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    /**
     * Six statements - one per section query - for a workspace with one
     * ticket and for one with many projects and tickets alike: nothing is
     * loaded per project or per ticket.
     */
    @Test
    void theNumberOfStatementsDoesNotGrowWithTheWorkspace() {
        Project first = project(own, "Core", "CORE");
        ticket(first, TicketStatus.TODO, TicketPriority.LOW, ana, T0);
        long small = statementsFor(anaActor);

        for (int p = 0; p < 5; p++) {
            Project project = project(own, "Project " + p, "PRJ" + p);
            for (int t = 0; t < 12; t++) {
                User assignee = switch (t % 3) {
                    case 0 -> ana;
                    case 1 -> pedro;
                    default -> null;
                };
                ticket(project, TicketStatus.values()[t % 5], TicketPriority.values()[t % 4], assignee,
                        T0.plusMinutes(p * 100L + t));
            }
        }
        long large = statementsFor(anaActor);

        assertThat(small).as("statements for 1 project / 1 ticket").isEqualTo(6);
        assertThat(large).as("statements for 6 projects / 61 tickets").isEqualTo(6);
        DashboardResponse dashboard = dashboard(anaActor);
        assertThat(dashboard.projects()).hasSize(6);
        assertThat(dashboard.assignedToMe().tickets()).hasSize(10);
        assertThat(dashboard.recentlyUpdated()).hasSize(8);
    }

    // ------------------------------------------------------------------

    private long statementsFor(AuthenticatedUser actor) {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();
        dashboardService.getByWorkspace(actor, actor.workspaceId());
        return statistics.getPrepareStatementCount();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /** From an empty persistence context, as a real request is served. */
    private DashboardResponse dashboard(AuthenticatedUser actor) {
        entityManager.flush();
        entityManager.clear();
        return dashboardService.getByWorkspace(actor, actor.workspaceId());
    }

    private User user(Workspace workspace, String name, UserRole role) {
        String email = "dashboard-" + tag + "-" + name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        return userRepository.saveAndFlush(new User(name, email, "hash", role, workspace));
    }

    private Project project(Workspace workspace, String name, String key) {
        return projectRepository.saveAndFlush(new Project(name, key, null, workspace));
    }

    /** Numbered per project; updated_at overwritten in SQL, bypassing the entity's @PreUpdate. */
    private Ticket ticket(Project project, TicketStatus status, TicketPriority priority, User assignee,
            OffsetDateTime updatedAt) {
        long number = nextNumbers.merge(project.getId(), 1L, Long::sum);
        Ticket ticket = ticketRepository.saveAndFlush(new Ticket(number, project.getKey() + " ticket " + number,
                "A description the dashboard never loads", status, priority, project, ana, assignee));
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE tickets SET updated_at = :updatedAt WHERE id = :id")
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", ticket.getId())
                .executeUpdate();
        return ticket;
    }
}
