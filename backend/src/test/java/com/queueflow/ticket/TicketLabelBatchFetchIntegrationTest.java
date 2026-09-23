package com.queueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.queueflow.activity.ActivityService;
import com.queueflow.label.Label;
import com.queueflow.label.LabelRepository;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.project.Project;
import com.queueflow.project.ProjectRepository;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

import jakarta.persistence.EntityManagerFactory;

/**
 * Proves the ticket list does not load labels with one query per ticket
 * (N+1), using Hibernate's built-in Statistics - enabled for this test
 * class only, no extra dependency. TicketResponse.from(...) reads every
 * listed ticket's LAZY label collection; with Ticket.labels'
 * {@code @BatchSize(size = 50)}, the first access initializes all pending
 * collections of the listed tickets in one query.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TicketService.class, ActivityService.class})
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TicketLabelBatchFetchIntegrationTest {

    private static final int TICKET_COUNT = 12;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void listingTicketsLoadsAllLabelCollectionsInOneBatchNotOneQueryPerTicket() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        User creator = userRepository.saveAndFlush(
                new User("Creator", "batch-creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));
        Label urgent = labelRepository.saveAndFlush(new Label("urgent", workspace));
        Label bug = labelRepository.saveAndFlush(new Label("Bug", workspace));
        Label api = labelRepository.saveAndFlush(new Label("api", workspace));

        // Every ticket but #1 gets labels, with varying combinations.
        List<Ticket> tickets = new ArrayList<>();
        for (int number = 1; number <= TICKET_COUNT; number++) {
            Ticket ticket = new Ticket(number, "Ticket " + number, null, TicketStatus.TODO, TicketPriority.LOW,
                    project, creator, null);
            if (number > 1) {
                ticket.addLabel(urgent);
            }
            if (number % 2 == 0) {
                ticket.addLabel(bug);
            }
            if (number % 3 == 0) {
                ticket.addLabel(api);
            }
            tickets.add(ticketRepository.save(ticket));
        }
        // Start from an empty persistence context, as a real request does:
        // nothing (tickets, project, label collections) is already loaded.
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<TicketResponse> responses = ticketService.getByProject(project.getId());

        long statements = statistics.getPrepareStatementCount();
        // Expected: 1 project existence check + 1 ticket list + 1 project
        // (for displayKey/projectKey, shared by all tickets) + 1 batched
        // label load for all 12 collections = 4. Without batching it would
        // be 3 + 12 = 15 (one label query per ticket).
        assertThat(statements)
                .as("JDBC statements for listing %d tickets with labels", TICKET_COUNT)
                .isEqualTo(4L);
        assertThat(statistics.getCollectionLoadCount()).isEqualTo(TICKET_COUNT);

        // And the batched load returned the right labels for each ticket, in
        // case-insensitive name order.
        assertThat(responses).hasSize(TICKET_COUNT);
        Map<Long, List<String>> labelsByNumber = responses.stream().collect(Collectors.toMap(
                TicketResponse::ticketNumber,
                response -> response.labels().stream().map(LabelResponse::name).toList()));
        assertThat(labelsByNumber.get(1L)).isEmpty();
        assertThat(labelsByNumber.get(2L)).containsExactly("Bug", "urgent");
        assertThat(labelsByNumber.get(3L)).containsExactly("api", "urgent");
        assertThat(labelsByNumber.get(6L)).containsExactly("api", "Bug", "urgent");
        assertThat(labelsByNumber.get(7L)).containsExactly("urgent");
        UUID firstId = tickets.get(0).getId();
        assertThat(responses.get(0).id()).isEqualTo(firstId);
    }
}
