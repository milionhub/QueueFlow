package com.queueflow.common.web;

import static com.queueflow.security.TestActors.actorIn;
import static com.queueflow.security.TestActors.actorOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.queueflow.label.LabelService;
import com.queueflow.label.dto.CreateLabelRequest;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.project.ProjectService;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.security.AccessTokenService;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketService;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

import jakarta.persistence.EntityManager;

/**
 * Real uniqueness races against PostgreSQL, through the full HTTP stack
 * (MockMvc on the real application context, real SecurityConfig and
 * GlobalExceptionHandler). No @Transactional: the two competing
 * transactions must be genuinely separate and committed, so cleanup is
 * manual.
 *
 * Each race is made deterministic without timing guesses:
 * <ol>
 *   <li>Transaction A writes the row and then waits, uncommitted.</li>
 *   <li>Request B runs; A's row is invisible to it (READ COMMITTED), so B
 *       passes the service's own duplicate check and then blocks inside
 *       PostgreSQL on A's uncommitted unique-index entry.</li>
 *   <li>The test waits until PostgreSQL itself reports B as blocked on a
 *       lock, then lets A commit - so the database rejects B with
 *       unique_violation.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseConflictRaceIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private LabelService labelService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private Workspace workspace;
    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        workspace = workspaceRepository.save(new Workspace("Race Workspace " + UUID.randomUUID()));
        user = userRepository.save(new User("Racer", "racer-" + UUID.randomUUID() + "@example.com", "hash",
                UserRole.MEMBER, workspace));
        // A real access token for that user: requests B go through bearer authentication.
        bearer = "Bearer " + accessTokenService.issue(user.getId()).tokenValue();
    }

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        UUID w = workspace.getId();
        String tickets = "SELECT t.id FROM tickets t JOIN projects p ON p.id = t.project_id WHERE p.workspace_id = ?";
        jdbcTemplate.update("DELETE FROM activities WHERE ticket_id IN (" + tickets + ")", w);
        jdbcTemplate.update("DELETE FROM ticket_labels WHERE ticket_id IN (" + tickets + ")", w);
        jdbcTemplate.update("DELETE FROM tickets WHERE id IN (" + tickets + ")", w);
        jdbcTemplate.update("DELETE FROM labels WHERE workspace_id = ?", w);
        jdbcTemplate.update("DELETE FROM projects WHERE workspace_id = ?", w);
        jdbcTemplate.update("DELETE FROM users WHERE workspace_id = ?", w);
        jdbcTemplate.update("DELETE FROM workspaces WHERE id = ?", w);
    }

    /** Condition-based wait: returns as soon as PostgreSQL reports a session blocked on a lock. */
    private void awaitSessionBlockedOnLock() throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Integer blocked = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'",
                    Integer.class);
            if (blocked != null && blocked > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Request B never blocked on transaction A's uncommitted row");
    }

    /** Runs {@code work} in its own transaction, which stays open (uncommitted) until released. */
    private Future<?> holdTransactionOpen(Runnable work, CountDownLatch written, CountDownLatch release) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return executor.submit(() -> transaction.executeWithoutResult(status -> {
            work.run();
            written.countDown();
            try {
                assertThat(release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));
    }

    private static void assertGenericConflict(MvcResult result, String path) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(409);
        assertThat((String) JsonPath.read(body, "$.error")).isEqualTo("Conflict");
        assertThat((String) JsonPath.read(body, "$.message")).isEqualTo("Resource conflicts with existing data");
        assertThat((String) JsonPath.read(body, "$.path")).isEqualTo(path);
        assertThat(body).doesNotContain("uq_", "pk_", "constraint", "duplicate key", "SQL", "Exception");
    }

    @Test
    void concurrentLabelCreationRaceBecomes409AndOnlyOneLabelExists() throws Exception {
        CountDownLatch aWritten = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        // A: creates "Race" (INSERT executed immediately), stays uncommitted.
        Future<?> a = holdTransactionOpen(
                () -> labelService.create(actorIn(workspace), new CreateLabelRequest("Race")), aWritten, releaseA);
        assertThat(aWritten.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        // B: the real HTTP request for the same name. Its duplicate pre-check
        // cannot see A's row, so its INSERT blocks on the unique index.
        Future<MvcResult> b = executor.submit(() -> mockMvc.perform(post("/api/labels")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Race"}
                                """))
                .andReturn());
        awaitSessionBlockedOnLock();

        releaseA.countDown();
        a.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        MvcResult result = b.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertGenericConflict(result, "/api/labels");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM labels WHERE workspace_id = ? AND name = 'Race'", Integer.class,
                workspace.getId())).isEqualTo(1);
    }

    @Test
    void concurrentTicketLabelAttachRaceFailingAtCommitBecomes409WithoutDuplicateRowsOrActivities()
            throws Exception {
        UUID projectId = projectService.create(actorIn(workspace),
                new CreateProjectRequest("Race Project", "RACE", null)).id();
        UUID ticketId = ticketService.create(actorOf(user), new CreateTicketRequest(projectId, "Race ticket", null,
                TicketStatus.TODO, TicketPriority.LOW, null)).id();
        LabelResponse label = labelService.create(actorIn(workspace), new CreateLabelRequest("Bug"));

        CountDownLatch aWritten = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        // A: attaches the label and flushes the ticket_labels row, uncommitted.
        Future<?> a = holdTransactionOpen(() -> {
            labelService.addLabelToTicket(actorOf(user), ticketId, label.id());
            entityManager.flush();
        }, aWritten, releaseA);
        assertThat(aWritten.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        // B: the same PUT over HTTP. It sees no attached label (A's row is
        // invisible), so it records its own LABEL_ADDED and returns - its
        // ticket_labels INSERT only happens at COMMIT, where it blocks.
        Future<MvcResult> b = executor.submit(() -> mockMvc.perform(
                        put("/api/tickets/{ticketId}/labels/{labelId}", ticketId, label.id())
                                .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn());
        awaitSessionBlockedOnLock();

        releaseA.countDown();
        a.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        MvcResult result = b.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertGenericConflict(result, "/api/tickets/" + ticketId + "/labels/" + label.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ticket_labels WHERE ticket_id = ?", Integer.class, ticketId)).isEqualTo(1);
        // B's LABEL_ADDED rolled back with its failed commit: exactly one remains.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM activities WHERE ticket_id = ? AND type = 'LABEL_ADDED'", Integer.class,
                ticketId)).isEqualTo(1);
    }
}
