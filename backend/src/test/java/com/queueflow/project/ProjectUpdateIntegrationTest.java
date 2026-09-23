package com.queueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.queueflow.activity.ActivityService;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.project.dto.UpdateProjectRequest;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketService;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Real-PostgreSQL coverage for project key normalization/validation and
 * PATCH persistence: what is actually stored, exact updatedAt round-trip,
 * and that project edits and ticket-number allocation stay independent.
 * Branching logic is covered by ProjectServiceTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ProjectService.class, TicketService.class, ActivityService.class})
class ProjectUpdateIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Workspace workspace() {
        return workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
    }

    private Object rawColumn(String column, UUID projectId) {
        return entityManager.getEntityManager()
                .createNativeQuery("SELECT " + column + " FROM projects WHERE id = ?1")
                .setParameter(1, projectId)
                .getSingleResult();
    }

    private long projectCount() {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM projects").getSingleResult()).longValue();
    }

    private void newRequest() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void createStoresTheNormalizedKeyAndTrimmedName() {
        Workspace workspace = workspace();

        ProjectResponse response = projectService.create(
                new CreateProjectRequest(workspace.getId(), "  QueueFlow Core  ", " crm ", null));
        newRequest();

        assertThat(response.key()).isEqualTo("CRM");
        assertThat(response.name()).isEqualTo("QueueFlow Core");
        assertThat(rawColumn("key", response.id())).isEqualTo("CRM");
        assertThat(rawColumn("name", response.id())).isEqualTo("QueueFlow Core");
    }

    @Test
    void unicodeKeyThatExpandsBeyondTenCharactersNeverReachesTheDatabase() {
        Workspace workspace = workspace();
        long before = projectCount();

        // "ßßßßßß" upper-cases to "SSSSSSSSSSSS" (12): without the service
        // check this would fail inside PostgreSQL (VARCHAR(10)). It must be a
        // clean business-rule rejection with no SQL issued for the project.
        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest(
                workspace.getId(), "Sharp S", "ßßßßßß", null)))
                .isExactlyInstanceOf(BusinessRuleViolationException.class);
        newRequest();

        assertThat(projectCount()).isEqualTo(before);
    }

    @Test
    void patchPersistsChangesAndItsResponseMatchesTheStoredValuesExactly() {
        Workspace workspace = workspace();
        Project project = projectRepository.saveAndFlush(new Project("Original", "ECOM", "Old", workspace));
        OffsetDateTime createdAt = project.getCreatedAt();
        OffsetDateTime updatedAtBefore = project.getUpdatedAt();
        newRequest();

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("  Storefront  ");
        request.setDescription("Storefront and checkout");
        ProjectResponse response = projectService.update(project.getId(), request);

        assertThat(response.name()).isEqualTo("Storefront");
        assertThat(response.updatedAt()).isAfter(updatedAtBefore);
        assertThat(response.updatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(response.updatedAt().getNano() % 1_000).isZero();

        newRequest();
        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        // Exact equality (instant AND offset): the response serializes
        // identically to a later GET.
        assertThat(response.updatedAt()).isEqualTo(reloaded.getUpdatedAt());
        assertThat(reloaded.getName()).isEqualTo("Storefront");
        assertThat(reloaded.getDescription()).isEqualTo("Storefront and checkout");
        assertThat(reloaded.getKey()).isEqualTo("ECOM");
        assertThat(reloaded.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(reloaded.getNextTicketNumber()).isEqualTo(1L);
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void explicitNullDescriptionIsStoredAsNull() {
        Workspace workspace = workspace();
        Project project = projectRepository.saveAndFlush(new Project("Original", "ECOM", "Old", workspace));
        newRequest();

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setDescription(null);
        projectService.update(project.getId(), request);
        newRequest();

        assertThat(rawColumn("description", project.getId())).isNull();
        assertThat(rawColumn("name", project.getId())).isEqualTo("Original");
    }

    @Test
    void ticketNumberingContinuesAcrossProjectEditsAndTicketsDoNotTouchProjectUpdatedAt() {
        Workspace workspace = workspace();
        User creator = userRepository.saveAndFlush(
                new User("Creator", "g5-creator@example.com", "hash", UserRole.MEMBER, workspace));
        Project project = projectRepository.saveAndFlush(new Project("Original", "QF", null, workspace));
        OffsetDateTime updatedAtAtCreation = project.getUpdatedAt();
        newRequest();

        TicketResponse first = ticketService.create(new CreateTicketRequest(project.getId(), "First", null, TicketStatus.TODO,
                TicketPriority.LOW, creator.getId(), null));
        newRequest();
        // Allocating a ticket number UPDATEs the projects row, but it is not
        // a project edit: updatedAt stays put.
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getUpdatedAt())
                .isEqualTo(updatedAtAtCreation);

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("Renamed");
        projectService.update(project.getId(), request);
        newRequest();

        TicketResponse second = ticketService.create(new CreateTicketRequest(project.getId(), "Second", null, TicketStatus.TODO,
                TicketPriority.LOW, creator.getId(), null));
        newRequest();

        assertThat(first.displayKey()).isEqualTo("QF-1");
        assertThat(second.displayKey()).isEqualTo("QF-2");
        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getNextTicketNumber()).isEqualTo(3L);
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getKey()).isEqualTo("QF");
    }
}
