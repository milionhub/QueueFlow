package com.queueflow.label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
class LabelRepositoryTest {

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void labelIsPersistedReferencingItsWorkspace() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        Label saved = labelRepository.saveAndFlush(new Label("bug", workspace));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getWorkspace().getId()).isEqualTo(workspace.getId());
    }

    @Test
    void sameLabelNameIsRejectedWithinTheSameWorkspace() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        labelRepository.saveAndFlush(new Label("bug", workspace));

        Label duplicate = new Label("bug", workspace);

        assertThatThrownBy(() -> labelRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameLabelNameIsAllowedAcrossDifferentWorkspaces() {
        Workspace first = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Workspace second = workspaceRepository.saveAndFlush(new Workspace("Globex"));

        labelRepository.saveAndFlush(new Label("bug", first));
        Label saved = labelRepository.saveAndFlush(new Label("bug", second));

        assertThat(saved.getId()).isNotNull();
        assertThat(labelRepository.existsByWorkspaceIdAndName(first.getId(), "bug")).isTrue();
        assertThat(labelRepository.existsByWorkspaceIdAndName(second.getId(), "bug")).isTrue();
    }

    @Test
    void findByWorkspaceIdAndNameReturnsMatchingLabel() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        labelRepository.saveAndFlush(new Label("urgent", workspace));

        Optional<Label> found = labelRepository.findByWorkspaceIdAndName(workspace.getId(), "urgent");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("urgent");
    }

    @Test
    void findByWorkspaceIdAndNameReturnsEmptyWhenNoMatch() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        assertThat(labelRepository.findByWorkspaceIdAndName(workspace.getId(), "missing")).isEmpty();
    }

    @Test
    void existsByWorkspaceIdAndNameReflectsPersistedState() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        labelRepository.saveAndFlush(new Label("frontend", workspace));

        assertThat(labelRepository.existsByWorkspaceIdAndName(workspace.getId(), "frontend")).isTrue();
        assertThat(labelRepository.existsByWorkspaceIdAndName(workspace.getId(), "backend")).isFalse();
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
    void workspaceListReturnsOnlyThatWorkspacesLabelsOrderedCaseInsensitivelyByName() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        Workspace other = workspaceRepository.saveAndFlush(new Workspace("Globex"));
        // Mixed case on purpose: a case-sensitive (byte-order) sort would put
        // "Bug" before "api", since every capitalized name sorts first.
        Label urgent = labelRepository.saveAndFlush(new Label("urgent", workspace));
        Label bug = labelRepository.saveAndFlush(new Label("Bug", workspace));
        Label frontend = labelRepository.saveAndFlush(new Label("frontend", workspace));
        Label api = labelRepository.saveAndFlush(new Label("api", workspace));
        labelRepository.saveAndFlush(new Label("aaa-other-workspace", other));
        entityManager.clear();

        List<Label> labels = labelRepository.findAllInWorkspaceSortedByName(workspace.getId());

        assertThat(labels).extracting(Label::getName).containsExactly("api", "Bug", "frontend", "urgent");
        assertThat(labels).extracting(Label::getId)
                .containsExactly(api.getId(), bug.getId(), frontend.getId(), urgent.getId());
    }

    @Test
    void workspaceListBreaksCaseOnlyTiesByNameBeforeId() {
        // "Bug" and "bug" legitimately coexist: label uniqueness is exact-case.
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));
        String earlier = sortsBefore("Bug", "bug") ? "Bug" : "bug";
        String later = earlier.equals("Bug") ? "bug" : "Bug";
        // Insert the later-sorting spelling FIRST, so it gets the smaller id:
        // id order and name order now disagree, and only the "name" key can
        // produce the expected result.
        Label laterLabel = labelRepository.saveAndFlush(new Label(later, workspace));
        Label frontend = labelRepository.saveAndFlush(new Label("Frontend", workspace));
        Label earlierLabel = labelRepository.saveAndFlush(new Label(earlier, workspace));
        entityManager.clear();

        assertThat(labelRepository.findAllInWorkspaceSortedByName(workspace.getId()))
                .extracting(Label::getId)
                .containsExactly(earlierLabel.getId(), laterLabel.getId(), frontend.getId());
    }

    @Test
    void workspaceListIsEmptyForWorkspaceWithoutLabels() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        assertThat(labelRepository.findAllInWorkspaceSortedByName(workspace.getId())).isEmpty();
    }
}
