package com.queueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.project.dto.UpdateProjectRequest;
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
                new ProjectResponse(generatedId, "E-Commerce", "ECOM", "Storefront", workspaceId, timestamp, timestamp));
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
    void getByIdReturnsMappedResponse() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        Project project = persistedProject(projectId, "E-Commerce", "ECOM", "Storefront", workspace, 5L, timestamp);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getById(projectId);

        assertThat(response).isEqualTo(
                new ProjectResponse(projectId, "E-Commerce", "ECOM", "Storefront", workspaceId, timestamp, timestamp));
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

    // ---------------------------------------------------------------
    // LIST BY WORKSPACE
    // ---------------------------------------------------------------

    @Test
    void getByWorkspaceReturnsMappedProjectsPreservingRepositoryOrder() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        Project zeta = persistedProject(UUID.randomUUID(), "Zeta", "ZETA", "Last", workspace, 4L, timestamp);
        Project alpha = persistedProject(UUID.randomUUID(), "Alpha", "ALPHA", null, workspace, 1L, timestamp);
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        // Deliberately not name-sorted: the service must not re-sort.
        when(projectRepository.findAllInWorkspaceSortedByName(workspaceId)).thenReturn(List.of(zeta, alpha));

        List<ProjectResponse> responses = projectService.getByWorkspace(workspaceId);

        assertThat(responses).extracting(ProjectResponse::id).containsExactly(zeta.getId(), alpha.getId());
        assertThat(responses.get(0)).isEqualTo(new ProjectResponse(zeta.getId(), "Zeta", "ZETA", "Last",
                workspaceId, timestamp, timestamp));
    }

    @Test
    void getByWorkspaceReturnsEmptyListForExistingWorkspaceWithoutProjects() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(projectRepository.findAllInWorkspaceSortedByName(workspaceId)).thenReturn(List.of());

        assertThat(projectService.getByWorkspace(workspaceId)).isEmpty();
    }

    @Test
    void getByWorkspaceThrowsResourceNotFoundExceptionForUnknownWorkspaceWithoutQueryingProjects() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.existsById(workspaceId)).thenReturn(false);

        assertThatThrownBy(() -> projectService.getByWorkspace(workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(workspaceId.toString());

        verify(projectRepository, never()).findAllInWorkspaceSortedByName(any());
    }

    // ---------------------------------------------------------------
    // KEY CONTRACT (create)
    // ---------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "crm, CRM",
            "' qf ', QF",
            "SHOP2, SHOP2",
            "backend1, BACKEND1",
            "ab, AB",
            "abcdefghij, ABCDEFGHIJ"})
    void createAcceptsValidKeysAndStoresTheNormalizedValue(String rawKey, String expectedKey) {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, expectedKey)).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.create(
                new CreateProjectRequest(workspaceId, "Project", rawKey, null));

        assertThat(response.key()).isEqualTo(expectedKey);
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo(expectedKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",            // too short
            "ABCDEFGHIJK",  // too long
            "A-B",          // punctuation
            "E COM",        // inner space
            "ABC_",         // underscore
            "\uD83D\uDD25\uD83D\uDD25", // emoji
            "\u00C4\u00D6X",       // non-ASCII letters (upper-case forms stay non-ASCII)
            "\u00DF\u00DF\u00DF\u00DF\u00DF\u00DF", // "ßßßßßß": 6 chars, upper-cases to 12 ("SS" x 6)
            "   "})         // blank
    void createRejectsInvalidKeysBeforeAnyDatabaseAccess(String rawKey) {
        UUID workspaceId = UUID.randomUUID();

        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest(workspaceId, "Project", rawKey, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("key");

        // Rejected before the workspace lookup, the duplicate check or the
        // insert: nothing can reach PostgreSQL as a constraint/length error.
        verifyNoInteractions(workspaceRepository, projectRepository);
    }

    @Test
    void createRejectsKeyWhoseUpperCaseExpansionExceedsTenCharacters() {
        // Passes the request-level @Size(max = 10) (6 characters), but
        // Locale.ROOT upper-casing expands each "ß" to "SS" -> 12 characters,
        // which would not fit projects.key VARCHAR(10).
        String sharpS = "\u00DF\u00DF\u00DF\u00DF\u00DF\u00DF";
        assertThat(sharpS).hasSize(6);
        assertThat(sharpS.toUpperCase(Locale.ROOT)).isEqualTo("SSSSSSSSSSSS");

        assertThatThrownBy(() -> projectService.create(
                new CreateProjectRequest(UUID.randomUUID(), "Project", sharpS, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void createRejectsNullKey() {
        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest(UUID.randomUUID(), "Project", null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verifyNoInteractions(workspaceRepository, projectRepository);
    }

    // ---------------------------------------------------------------
    // NAME RULE (create) - same as update(): trim, not blank, max on trimmed
    // ---------------------------------------------------------------

    private Project createAndCaptureSaved(String rawName, String description) {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findById(workspaceId))
                .thenReturn(Optional.of(persistedWorkspace(workspaceId, "Acme Inc.")));
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, "QF")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectService.create(new CreateProjectRequest(workspaceId, rawName, "qf", description));

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createTrimsNameAndStoresDescriptionAsGiven() {
        Project saved = createAndCaptureSaved("  QueueFlow Core  ", "  Kept as given  ");

        assertThat(saved.getName()).isEqualTo("QueueFlow Core");
        assertThat(saved.getDescription()).isEqualTo("  Kept as given  ");
    }

    @Test
    void createAcceptsMaximumLengthNameAfterTrimming() {
        Project saved = createAndCaptureSaved(" " + "a".repeat(255) + " ", null);

        assertThat(saved.getName()).hasSize(255);
    }

    @Test
    void createRejectsNameLongerThanTheLimitAfterTrimming() {
        assertThatThrownBy(() -> projectService.create(
                new CreateProjectRequest(UUID.randomUUID(), "a".repeat(256), "qf", null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("255");

        verifyNoInteractions(workspaceRepository, projectRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void createRejectsBlankNameBeforeAnyDatabaseAccess(String blankName) {
        assertThatThrownBy(() -> projectService.create(
                new CreateProjectRequest(UUID.randomUUID(), blankName, "qf", null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(workspaceRepository, projectRepository);
    }

    // ---------------------------------------------------------------
    // UPDATE (PATCH)
    // ---------------------------------------------------------------

    private static final OffsetDateTime ORIGINAL_TIMESTAMP = OffsetDateTime.parse("2026-01-01T09:00:00Z");

    /** A persisted-looking project with a fixed past timestamp, returned by findById. */
    private Project existingProject(UUID projectId, Workspace workspace) {
        Project project = persistedProject(projectId, "Original Name", "ECOM", "Original description", workspace, 8L,
                ORIGINAL_TIMESTAMP);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        return project;
    }

    private static UpdateProjectRequest patch() {
        return new UpdateProjectRequest();
    }

    private static void assertImmutableFieldsUnchanged(Project project, Workspace workspace) {
        assertThat(project.getKey()).isEqualTo("ECOM");
        assertThat(project.getWorkspace()).isSameAs(workspace);
        assertThat(project.getNextTicketNumber()).isEqualTo(8L);
        assertThat(project.getCreatedAt()).isEqualTo(ORIGINAL_TIMESTAMP);
    }

    private static void assertUpdatedAtAdvancedInUtcMicros(Project project) {
        assertThat(project.getUpdatedAt()).isAfter(ORIGINAL_TIMESTAMP);
        assertThat(project.getUpdatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(project.getUpdatedAt().getNano() % 1_000).isZero();
    }

    @Test
    void updateNameOnlyTrimsItAndKeepsDescription() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setName("  Storefront v2  ");

        ProjectResponse response = projectService.update(projectId, request);

        assertThat(project.getName()).isEqualTo("Storefront v2");
        assertThat(project.getDescription()).isEqualTo("Original description");
        assertUpdatedAtAdvancedInUtcMicros(project);
        assertImmutableFieldsUnchanged(project, workspace);
        assertThat(response).isEqualTo(ProjectResponse.from(project));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateDescriptionOnlyKeepsName() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setDescription("New description");

        ProjectResponse response = projectService.update(projectId, request);

        assertThat(project.getName()).isEqualTo("Original Name");
        assertThat(project.getDescription()).isEqualTo("New description");
        assertThat(response.description()).isEqualTo("New description");
        assertUpdatedAtAdvancedInUtcMicros(project);
        assertImmutableFieldsUnchanged(project, workspace);
    }

    @Test
    void updateBothFieldsLeavesKeyWorkspaceCounterAndCreatedAtUntouched() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setName("Renamed");
        request.setDescription("Described");

        ProjectResponse response = projectService.update(projectId, request);

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.description()).isEqualTo("Described");
        assertThat(response.key()).isEqualTo("ECOM");
        assertThat(response.workspaceId()).isEqualTo(workspace.getId());
        assertThat(response.createdAt()).isEqualTo(ORIGINAL_TIMESTAMP);
        assertThat(response.updatedAt()).isEqualTo(project.getUpdatedAt());
        assertUpdatedAtAdvancedInUtcMicros(project);
        assertImmutableFieldsUnchanged(project, workspace);
    }

    @Test
    void updateWithExplicitNullDescriptionClearsIt() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setDescription(null);

        ProjectResponse response = projectService.update(projectId, request);

        assertThat(project.getDescription()).isNull();
        assertThat(response.description()).isNull();
        assertThat(project.getName()).isEqualTo("Original Name");
        assertUpdatedAtAdvancedInUtcMicros(project);
    }

    @Test
    void emptyPatchChangesNothingIncludingUpdatedAt() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);

        ProjectResponse response = projectService.update(projectId, patch());

        assertThat(project.getName()).isEqualTo("Original Name");
        assertThat(project.getDescription()).isEqualTo("Original description");
        assertThat(project.getUpdatedAt()).isEqualTo(ORIGINAL_TIMESTAMP);
        assertThat(response.updatedAt()).isEqualTo(ORIGINAL_TIMESTAMP);
        assertImmutableFieldsUnchanged(project, workspace);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void patchWithSameValuesIsANoOpAndKeepsUpdatedAt() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setName("  Original Name ");   // equal after trimming
        request.setDescription("Original description");

        projectService.update(projectId, request);

        assertThat(project.getUpdatedAt()).isEqualTo(ORIGINAL_TIMESTAMP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void updateRejectsBlankNameWithoutChangingAnything(String blankName) {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setName(blankName);
        request.setDescription("Would change");

        assertThatThrownBy(() -> projectService.update(projectId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("name");

        // Validation happens before any mutation: the valid description in
        // the same request was not applied either.
        assertThat(project.getName()).isEqualTo("Original Name");
        assertThat(project.getDescription()).isEqualTo("Original description");
        assertThat(project.getUpdatedAt()).isEqualTo(ORIGINAL_TIMESTAMP);
    }

    @Test
    void updateRejectsOverlongNameWithoutChangingAnything() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setName("a".repeat(256));

        assertThatThrownBy(() -> projectService.update(projectId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("255");

        assertThat(project.getName()).isEqualTo("Original Name");
        assertThat(project.getUpdatedAt()).isEqualTo(ORIGINAL_TIMESTAMP);
    }

    @Test
    void updateAcceptsMaximumLengthNameAfterTrimming() {
        Workspace workspace = persistedWorkspace(UUID.randomUUID(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setName(" " + "a".repeat(255) + " ");

        projectService.update(projectId, request);

        assertThat(project.getName()).hasSize(255);
    }

    @Test
    void updateThrowsResourceNotFoundExceptionForUnknownProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        UpdateProjectRequest request = patch();
        request.setName("Anything");

        assertThatThrownBy(() -> projectService.update(projectId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(projectId.toString());
    }
}
