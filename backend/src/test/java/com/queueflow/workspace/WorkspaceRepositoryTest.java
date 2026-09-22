package com.queueflow.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class WorkspaceRepositoryTest {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void persistedWorkspaceReceivesDatabaseGeneratedUuidAndTimestamps() {
        Workspace saved = workspaceRepository.saveAndFlush(new Workspace("Acme Inc."));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Acme Inc.");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
