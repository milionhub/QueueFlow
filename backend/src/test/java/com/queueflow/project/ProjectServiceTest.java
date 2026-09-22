package com.queueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Fast unit tests with mocked repositories - persistence behavior itself is
 * already covered by ProjectRepositoryTest against the real database.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, workspaceRepository);
    }

    private static Workspace persistedWorkspace(UUID id, String name) {
        Workspace workspace = new Workspace(name);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static Project persistedProject(UUID id, String name, String key, String description,
            Workspace workspace, long nextTicketNumber, OffsetDateTime timestamp) {
        Project project = new Project(name, key, description, workspace);
        ReflectionTestUtils.setField(project, "id", id);
        ReflectionTestUtils.setField(project, "nextTicketNumber", nextTicketNumber);
        ReflectionTestUtils.setField(project, "createdAt", timestamp);
        ReflectionTestUtils.setField(project, "updatedAt", timestamp);
        return project;
    }

    @Test
    void createLoadsWorkspaceNormalizesKeyAndSavesProjectWithCorrectFields() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        CreateProjectRequest request = new CreateProjectRequest(workspaceId, "E-Commerce", "  ecom  ", "Storefront");

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, "ECOM")).thenReturn(false);

        UUID generatedId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project argument = invocation.getArgument(0);
            ReflectionTestUtils.setField(argument, "id", generatedId);
            ReflectionTestUtils.setField(argument, "nextTicketNumber", 1L);
            ReflectionTestUtils.setField(argument, "createdAt", timestamp);
            ReflectionTestUtils.setField(argument, "updatedAt", timestamp);
            return argument;
        });

        ProjectResponse response = projectService.create(request);

        verify(workspaceRepository).findById(workspaceId);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("E-Commerce");
        assertThat(saved.getKey()).isEqualTo("ECOM");
        assertThat(saved.getDescription()).isEqualTo("Storefront");
        assertThat(saved.getWorkspace()).isSameAs(workspace);

        assertThat(response).isEqualTo(
                new ProjectResponse(generatedId, "E-Commerce", "ECOM", "Storefront", workspaceId, 1L, timestamp, timestamp));
    }

    @Test
    void createThrowsResourceNotFoundExceptionWhenWorkspaceMissing() {
        UUID workspaceId = UUID.randomUUID();
        CreateProjectRequest request = new CreateProjectRequest(workspaceId, "E-Commerce", "ECOM", null);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(workspaceId.toString());

        verify(projectRepository, never()).save(any());
    }

    @Test
    void createThrowsResourceAlreadyExistsExceptionWhenNormalizedKeyIsDuplicate() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        // Raw key deliberately not already normalized - proves the duplicate
        // check happens against "ECOM", not the raw "  ecom  ".
        CreateProjectRequest request = new CreateProjectRequest(workspaceId, "E-Commerce", "  ecom  ", null);

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, "ECOM")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("ECOM");

        verify(projectRepository).existsByWorkspaceIdAndKey(workspaceId, "ECOM");
        verify(projectRepository, never()).save(any());
    }

    @Test
    void getByIdReturnsMappedResponseWithNextTicketNumber() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        Project project = persistedProject(projectId, "E-Commerce", "ECOM", "Storefront", workspace, 5L, timestamp);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getById(projectId);

        assertThat(response).isEqualTo(
                new ProjectResponse(projectId, "E-Commerce", "ECOM", "Storefront", workspaceId, 5L, timestamp, timestamp));
    }

    @Test
    void getByIdThrowsResourceNotFoundExceptionWhenMissing() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(projectId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(projectId.toString());
    }

    @Test
    void getByWorkspaceAndKeyNormalizesKeyAndReturnsResponse() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        Project project = persistedProject(projectId, "E-Commerce", "ECOM", null, workspace, 1L, timestamp);

        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "ECOM")).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getByWorkspaceAndKey(workspaceId, "  ecom  ");

        assertThat(response.key()).isEqualTo("ECOM");
        verify(projectRepository).findByWorkspaceIdAndKey(workspaceId, "ECOM");
    }

    @Test
    void getByWorkspaceAndKeyThrowsResourceNotFoundExceptionWhenMissing() {
        UUID workspaceId = UUID.randomUUID();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "ECOM")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getByWorkspaceAndKey(workspaceId, "ecom"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ECOM");
    }

    @Test
    void projectResponseExposesWorkspaceIdNotWorkspaceEntity() {
        RecordComponent[] components = ProjectResponse.class.getRecordComponents();

        assertThat(components).extracting(RecordComponent::getName).contains("workspaceId");

        RecordComponent workspaceIdComponent = Arrays.stream(components)
                .filter(component -> component.getName().equals("workspaceId"))
                .findFirst()
                .orElseThrow();
        assertThat(workspaceIdComponent.getType()).isEqualTo(UUID.class);
    }
}
