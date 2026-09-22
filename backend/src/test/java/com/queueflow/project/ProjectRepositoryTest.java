package com.queueflow.project;

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
}
