package com.queueflow.ticket;

import static com.queueflow.security.TestActors.actorIn;
import static com.queueflow.security.TestActors.actorOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.ProjectService;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.UpdateTicketRequest;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Proves a ticket PATCH is atomic against the real database: when a later
 * part of the request is rejected, the earlier, already-applied field
 * changes and their Activity rows are rolled back too.
 *
 * TicketService.update() applies title, description, status and priority
 * (recording an Activity for each - those INSERTs run immediately) before
 * it validates the new assignee. A @DataJpaTest cannot prove the rollback:
 * its test-managed transaction keeps the partial changes in the same
 * transaction and persistence context. So this class is plain
 * @SpringBootTest with no @Transactional - each service call commits or
 * rolls back for real - and cleans up manually.
 */
@SpringBootTest
class TicketUpdateRollbackIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;
    private Workspace otherWorkspace;
    private User alice;
    private User outsider;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        workspace = workspaceRepository.save(new Workspace("Rollback Workspace " + UUID.randomUUID()));
        otherWorkspace = workspaceRepository.save(new Workspace("Other Workspace " + UUID.randomUUID()));
        alice = userRepository.save(new User("Alice", "alice-" + UUID.randomUUID() + "@example.com", "hash",
                UserRole.MEMBER, workspace));
        outsider = userRepository.save(new User("Outsider", "outsider-" + UUID.randomUUID() + "@example.com",
                "hash", UserRole.MEMBER, otherWorkspace));
        UUID projectId = projectService.create(actorIn(workspace),
                new CreateProjectRequest("Rollback", "RLB", null)).id();
        ticketId = ticketService.create(actorOf(alice), new CreateTicketRequest(projectId, "Original title",
                "Original description", TicketStatus.BACKLOG, TicketPriority.LOW, null)).id();
    }

    @AfterEach
    void cleanUp() {
        for (Workspace w : List.of(workspace, otherWorkspace)) {
            String tickets = "SELECT t.id FROM tickets t JOIN projects p ON p.id = t.project_id WHERE p.workspace_id = ?";
            jdbcTemplate.update("DELETE FROM activities WHERE ticket_id IN (" + tickets + ")", w.getId());
            jdbcTemplate.update("DELETE FROM tickets WHERE id IN (" + tickets + ")", w.getId());
            jdbcTemplate.update("DELETE FROM projects WHERE workspace_id = ?", w.getId());
            jdbcTemplate.update("DELETE FROM users WHERE workspace_id = ?", w.getId());
            jdbcTemplate.update("DELETE FROM workspaces WHERE id = ?", w.getId());
        }
    }

    private Map<String, Object> ticketRow() {
        return jdbcTemplate.queryForMap(
                "SELECT title, description, status, priority, assignee_id, updated_at FROM tickets WHERE id = ?",
                ticketId);
    }

    private List<String> activityTypes() {
        return jdbcTemplate.queryForList(
                "SELECT type FROM activities WHERE ticket_id = ? ORDER BY created_at, id", String.class, ticketId);
    }

    @Test
    void rejectedAssigneeRollsBackEveryEarlierFieldChangeAndActivityOfTheSamePatch() {
        Map<String, Object> before = ticketRow();
        assertThat(activityTypes()).containsExactly("TICKET_CREATED");

        // Four valid changes are applied (each recording an Activity) before
        // the assignee from another workspace is rejected as not found.
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Changed title");
        request.setDescription(null);
        request.setStatus(TicketStatus.DONE);
        request.setPriority(TicketPriority.CRITICAL);
        request.setAssigneeId(outsider.getId());

        assertThatThrownBy(() -> ticketService.update(actorOf(alice), ticketId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found: " + outsider.getId());

        // Nothing of the PATCH survived: fields, updated_at and activities
        // are exactly as before.
        assertThat(ticketRow()).isEqualTo(before);
        assertThat(activityTypes()).containsExactly("TICKET_CREATED");
    }

    @Test
    void theSameValidChangesWithoutTheBadAssigneeDoCommit() {
        // Control case: proves the rollback test above really exercises
        // changes that would otherwise have been persisted.
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Changed title");
        request.setDescription(null);
        request.setStatus(TicketStatus.DONE);
        request.setPriority(TicketPriority.CRITICAL);

        ticketService.update(actorOf(alice), ticketId, request);

        Map<String, Object> after = ticketRow();
        assertThat(after.get("title")).isEqualTo("Changed title");
        assertThat(after.get("description")).isNull();
        assertThat(after.get("status")).isEqualTo("DONE");
        assertThat(after.get("priority")).isEqualTo("CRITICAL");
        assertThat(activityTypes()).containsExactly(
                "TICKET_CREATED", "TITLE_CHANGED", "DESCRIPTION_CHANGED", "STATUS_CHANGED", "PRIORITY_CHANGED");
    }
}
