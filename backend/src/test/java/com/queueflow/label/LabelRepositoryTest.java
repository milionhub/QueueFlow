package com.queueflow.label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
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
}
