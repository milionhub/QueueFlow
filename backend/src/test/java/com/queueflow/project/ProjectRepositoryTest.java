package com.queueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void projectIsPersistedReferencingItsWorkspace() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        Project saved = projectRepository.saveAndFlush(
                new Project("E-Commerce", "ECOM", "Storefront and checkout", workspace));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getWorkspace().getId()).isEqualTo(workspace.getId());

        entityManager.clear();

        Project reloaded = projectRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(reloaded.getWorkspace().getName()).isEqualTo("Acme Inc.");
    }

    @Test
    void newProjectDefaultsNextTicketNumberToOne() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        Project saved = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));

        assertThat(saved.getNextTicketNumber()).isEqualTo(1L);
    }

    @Test
    void descriptionCanBeNull() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        Project saved = projectRepository.saveAndFlush(new Project("Mobile App", "MOB", null, workspace));

        assertThat(saved.getDescription()).isNull();
    }

    @Test
    void findByWorkspaceIdAndKeyReturnsMatchingProject() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        projectRepository.saveAndFlush(new Project("Public API", "API", null, workspace));

        Optional<Project> found = projectRepository.findByWorkspaceIdAndKey(workspace.getId(), "API");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Public API");
    }

    @Test
    void findByWorkspaceIdAndKeyReturnsEmptyWhenNoMatch() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        assertThat(projectRepository.findByWorkspaceIdAndKey(workspace.getId(), "MISSING")).isEmpty();
    }

    @Test
    void existsByWorkspaceIdAndKeyReflectsPersistedState() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));

        assertThat(projectRepository.existsByWorkspaceIdAndKey(workspace.getId(), "ECOM")).isTrue();
        assertThat(projectRepository.existsByWorkspaceIdAndKey(workspace.getId(), "NOPE")).isFalse();
    }

    @Test
    void sameKeyIsRejectedWithinTheSameWorkspace() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, workspace));

        Project duplicate = new Project("Ecommerce Rebuild", "ECOM", null, workspace);

        assertThatThrownBy(() -> projectRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameKeyIsAllowedAcrossDifferentWorkspaces() {
        Workspace first = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Workspace second = workspaceRepository.saveAndFlush(new Workspace("Globex"));

        projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, first));
        Project saved = projectRepository.saveAndFlush(new Project("E-Commerce", "ECOM", null, second));

        assertThat(saved.getId()).isNotNull();
        assertThat(projectRepository.existsByWorkspaceIdAndKey(first.getId(), "ECOM")).isTrue();
        assertThat(projectRepository.existsByWorkspaceIdAndKey(second.getId(), "ECOM")).isTrue();
    }

    /** No-op UPDATE: writes a new row version, moving the row to the end of heap/index order. */
    private void rewriteRow(UUID projectId) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE projects SET name = name WHERE id = ?1")
                .setParameter(1, projectId)
                .executeUpdate();
    }


    /**
     * Asks PostgreSQL itself whether a sorts before b under the database's
     * own collation - the rule the secondary "name ASC" key uses. Keeps the
     * case-only-tie expectation exact without hard-coding a collation
     * (musl/alpine sorts "Alpha" before "alpha"; glibc may not).
     */
    private boolean sortsBefore(String a, String b) {
        return (Boolean) entityManager.getEntityManager()
                .createNativeQuery("SELECT CAST(?1 AS text) < CAST(?2 AS text)")
                .setParameter(1, a)
                .setParameter(2, b)
                .getSingleResult();
    }

    @Test
    void workspaceListReturnsOnlyThatWorkspacesProjectsOrderedCaseInsensitivelyByName() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Workspace other = workspaceRepository.saveAndFlush(new Workspace("Globex"));
        // Mixed case on purpose: a case-sensitive (byte-order) sort would put
        // "beta tools" last, after every capitalized name.
        Project zeta = projectRepository.saveAndFlush(new Project("Zeta Platform", "ZETA", null, workspace));
        Project alpha = projectRepository.saveAndFlush(new Project("Alpha Web", "ALPHA", null, workspace));
        Project mobile = projectRepository.saveAndFlush(new Project("Mobile App", "MOB", null, workspace));
        Project beta = projectRepository.saveAndFlush(new Project("beta tools", "BETA", null, workspace));
        projectRepository.saveAndFlush(new Project("Aardvark", "AARD", null, other));
        entityManager.clear();

        List<Project> projects = projectRepository.findAllInWorkspaceSortedByName(workspace.getId());

        assertThat(projects).extracting(Project::getName)
                .containsExactly("Alpha Web", "beta tools", "Mobile App", "Zeta Platform");
        assertThat(projects).extracting(Project::getId)
                .containsExactly(alpha.getId(), beta.getId(), mobile.getId(), zeta.getId());
    }

    @Test
    void workspaceListBreaksCaseOnlyTiesByNameBeforeId() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        String earlier = sortsBefore("Alpha", "alpha") ? "Alpha" : "alpha";
        String later = earlier.equals("Alpha") ? "alpha" : "Alpha";
        // Insert the later-sorting spelling FIRST, so it gets the smaller id:
        // id order and name order now disagree, and only the "name" key can
        // produce the expected result.
        Project laterProject = projectRepository.saveAndFlush(new Project(later, "LATER", null, workspace));
        Project beta = projectRepository.saveAndFlush(new Project("Beta", "BETA", null, workspace));
        Project earlierProject = projectRepository.saveAndFlush(new Project(earlier, "EARLIER", null, workspace));
        entityManager.clear();

        assertThat(projectRepository.findAllInWorkspaceSortedByName(workspace.getId()))
                .extracting(Project::getId)
                .containsExactly(earlierProject.getId(), laterProject.getId(), beta.getId());
    }

    @Test
    void workspaceListBreaksEqualNamesByIdAscending() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Project first = projectRepository.saveAndFlush(new Project("Shared", "SHA", null, workspace));
        Project second = projectRepository.saveAndFlush(new Project("Shared", "SHB", null, workspace));
        Project third = projectRepository.saveAndFlush(new Project("Shared", "SHC", null, workspace));
        // uuidv7 ids ascend in insertion order (lowercase-hex comparison
        // matches PostgreSQL uuid ordering).
        assertThat(List.of(first, second, third)).extracting(p -> p.getId().toString()).isSorted();

        // Reverse the physical order so only the id tie-breaker can yield id order.
        rewriteRow(third.getId());
        rewriteRow(second.getId());
        rewriteRow(first.getId());
        entityManager.clear();

        assertThat(projectRepository.findAllInWorkspaceSortedByName(workspace.getId()))
                .extracting(Project::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void workspaceListIsEmptyForWorkspaceWithoutProjects() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        assertThat(projectRepository.findAllInWorkspaceSortedByName(workspace.getId())).isEmpty();
    }
}
