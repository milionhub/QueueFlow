package com.queueflow.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.dto.WorkspaceResponse;

/**
 * Fast unit tests with a mocked repository - persistence behavior itself
 * (DB-generated UUID/timestamps, constraints) is already covered by
 * WorkspaceRepositoryTest against the real database.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    /** The caller of every operation below; its workspace is the only one the service can see. */
    private static final AuthenticatedUser ACTOR =
            new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UserRole.MEMBER);

    @Mock
    private WorkspaceRepository workspaceRepository;

    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(workspaceRepository);
    }

    private static Workspace persistedWorkspace(UUID id, String name, OffsetDateTime timestamp) {
        Workspace workspace = new Workspace(name);
        ReflectionTestUtils.setField(workspace, "id", id);
        ReflectionTestUtils.setField(workspace, "createdAt", timestamp);
        ReflectionTestUtils.setField(workspace, "updatedAt", timestamp);
        return workspace;
    }

    @Test
    void getByIdReturnsMappedResponse() {
        UUID id = ACTOR.workspaceId();
        OffsetDateTime timestamp = OffsetDateTime.now();
        Workspace workspace = persistedWorkspace(id, "Acme Inc.", timestamp);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(workspace));

        WorkspaceResponse response = workspaceService.getById(ACTOR, id);

        assertThat(response).isEqualTo(new WorkspaceResponse(id, "Acme Inc.", timestamp, timestamp));
    }

    @Test
    void anyWorkspaceButTheCallersIsNotFoundWithoutALookup() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> workspaceService.getById(ACTOR, id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Workspace not found: " + id);

        verifyNoInteractions(workspaceRepository);
    }
}
