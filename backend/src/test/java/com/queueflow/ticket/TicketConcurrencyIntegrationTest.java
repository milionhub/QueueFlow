package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.queueflow.activity.ActivityRepository;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Proves the per-project ticket-number counter is safe under real
 * concurrency, using genuinely separate threads/transactions/connections
 * against the real database - not a mocked lock.
 *
 * Deliberately plain @SpringBootTest with NO class/method-level
 * @Transactional: two concurrent transactions on two different DB
 * connections are exactly what this test needs to exercise, and the setup
 * data (workspace/user/project) must be actually committed so both
 * background threads' independent transactions can see it. A
 * @Transactional test method would wrap everything - setup included - in
 * one transaction that only the test thread participates in, which would
 * either make the setup invisible to the worker threads (under a
 * non-default isolation) or defeat the point of testing two independent
 * transactions entirely. Cleanup is therefore done manually in @AfterEach
 * rather than relying on automatic rollback.
 */
@SpringBootTest
class TicketConcurrencyIntegrationTest {

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

    private final List<UUID> createdTicketIds = new ArrayList<>();
    private Workspace workspace;
    private User creator;
    private Project project;

    @AfterEach
    void cleanUp() {
        // Each successful create() now also inserts a TICKET_CREATED
        // Activity row referencing the ticket via a FK with no cascade, so
        // activities must be deleted before their ticket.
        createdTicketIds.forEach(ticketId -> activityRepository
                .findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)
                .forEach(activity -> activityRepository.deleteById(activity.getId())));
        createdTicketIds.forEach(ticketRepository::deleteById);
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
    void concurrentTicketCreationForSameProjectAllocatesDistinctSequentialNumbers() throws Exception {
        workspace = workspaceRepository.saveAndFlush(new Workspace("Concurrency Test Workspace"));
        creator = userRepository.saveAndFlush(
                new User("Creator", "concurrency-creator@example.com", "hash", UserRole.MEMBER, workspace));
        project = projectRepository.saveAndFlush(new Project("Concurrency Project", "CONC", null, workspace));

        // Lines up both worker threads to call create() at essentially the
        // same instant, maximizing genuine lock contention on the project
        // row rather than relying on (flaky) thread-scheduling luck. The
        // barrier only coordinates the TEST threads - it is not part of,
        // and does not substitute for, the production locking mechanism.
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<TicketResponse> task = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return ticketService.create(new CreateTicketRequest(
                    project.getId(), "Concurrent ticket", null, TicketStatus.BACKLOG, TicketPriority.LOW,
                    creator.getId(), null));
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TicketResponse> future1 = executor.submit(task);
            Future<TicketResponse> future2 = executor.submit(task);

            TicketResponse response1 = future1.get(15, TimeUnit.SECONDS);
            TicketResponse response2 = future2.get(15, TimeUnit.SECONDS);
            createdTicketIds.add(response1.id());
            createdTicketIds.add(response2.id());

            assertThat(List.of(response1.ticketNumber(), response2.ticketNumber()))
                    .containsExactlyInAnyOrder(1L, 2L);

            Project reloadedProject = projectRepository.findById(project.getId()).orElseThrow();
            assertThat(reloadedProject.getNextTicketNumber()).isEqualTo(3L);

            long ticketCountForProject = ticketRepository.findAll().stream()
                    .filter(ticket -> ticket.getProject().getId().equals(project.getId()))
                    .count();
            assertThat(ticketCountForProject).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }
}
