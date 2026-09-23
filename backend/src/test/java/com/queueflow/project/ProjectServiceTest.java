package com.queueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import com.queueflow.common.exception.ForbiddenOperationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.project.dto.UpdateProjectRequest;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Fast unit tests with mocked repositories - persistence behavior itself is
 * already covered by ProjectRepositoryTest against the real database.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    /**
     * The caller of every operation below; its workspace is the only one the
     * service can see. An ADMIN, because creating and updating projects is
     * ADMIN-only (see the ROLE POLICY section for the MEMBER cases).
     */
    private static final AuthenticatedUser ACTOR =
            new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN);

    /** A MEMBER of the same workspace as ACTOR. */
    private static final AuthenticatedUser MEMBER =
            new AuthenticatedUser(UUID.randomUUID(), ACTOR.workspaceId(), UserRole.MEMBER);

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
        UUID workspaceId = ACTOR.workspaceId();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        CreateProjectRequest request = new CreateProjectRequest("E-Commerce", "  ecom  ", "Storefront");

        when(workspaceRepository.getReferenceById(workspaceId)).thenReturn(workspace);
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

        ProjectResponse response = projectService.create(ACTOR, request);

        verify(workspaceRepository).getReferenceById(workspaceId);

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
    void createThrowsResourceAlreadyExistsExceptionWhenNormalizedKeyIsDuplicate() {
        UUID workspaceId = ACTOR.workspaceId();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        // Raw key deliberately not already normalized - proves the duplicate
        // check happens against "ECOM", not the raw "  ecom  ".
        CreateProjectRequest request = new CreateProjectRequest("E-Commerce", "  ecom  ", null);

        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, "ECOM")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(ACTOR, request))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("ECOM");

        verify(projectRepository).existsByWorkspaceIdAndKey(workspaceId, "ECOM");
        verify(projectRepository, never()).save(any());
    }

    @Test
    void getByIdReturnsMappedResponse() {
        UUID workspaceId = ACTOR.workspaceId();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        Project project = persistedProject(projectId, "E-Commerce", "ECOM", "Storefront", workspace, 5L, timestamp);

        when(projectRepository.findByIdAndWorkspaceId(projectId, ACTOR.workspaceId())).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getById(ACTOR, projectId);

        assertThat(response).isEqualTo(
                new ProjectResponse(projectId, "E-Commerce", "ECOM", "Storefront", workspaceId, timestamp, timestamp));
    }

    @Test
    void createNeverLooksAtAnyWorkspaceButTheCallers() {
        UUID workspaceId = ACTOR.workspaceId();
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, "OPS")).thenReturn(false);
        when(workspaceRepository.getReferenceById(workspaceId)).thenReturn(persistedWorkspace(workspaceId, "Acme"));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectService.create(ACTOR, new CreateProjectRequest("Operations", "ops", null));

        verify(projectRepository).existsByWorkspaceIdAndKey(workspaceId, "OPS");
        verify(workspaceRepository).getReferenceById(workspaceId);
        verifyNoMoreInteractions(workspaceRepository);
    }

    @Test
    void getByIdThrowsResourceNotFoundExceptionWhenMissing() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndWorkspaceId(projectId, ACTOR.workspaceId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(ACTOR, projectId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(projectId.toString());
    }

    @Test
    void getByKeyNormalizesKeyAndReturnsResponse() {
        UUID workspaceId = ACTOR.workspaceId();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        Project project = persistedProject(projectId, "E-Commerce", "ECOM", null, workspace, 1L, timestamp);

        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "ECOM")).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getByKey(ACTOR, "  ecom  ");

        assertThat(response.key()).isEqualTo("ECOM");
        verify(projectRepository).findByWorkspaceIdAndKey(workspaceId, "ECOM");
    }

    @Test
    void getByKeyThrowsResourceNotFoundExceptionWhenMissing() {
        UUID workspaceId = ACTOR.workspaceId();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "ECOM")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getByKey(ACTOR, "ecom"))
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
        UUID workspaceId = ACTOR.workspaceId();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-23T10:15:30Z");
        Project zeta = persistedProject(UUID.randomUUID(), "Zeta", "ZETA", "Last", workspace, 4L, timestamp);
        Project alpha = persistedProject(UUID.randomUUID(), "Alpha", "ALPHA", null, workspace, 1L, timestamp);
        // Deliberately not name-sorted: the service must not re-sort.
        when(projectRepository.findAllInWorkspaceSortedByName(workspaceId)).thenReturn(List.of(zeta, alpha));

        List<ProjectResponse> responses = projectService.getByWorkspace(ACTOR, workspaceId);

        assertThat(responses).extracting(ProjectResponse::id).containsExactly(zeta.getId(), alpha.getId());
        assertThat(responses.get(0)).isEqualTo(new ProjectResponse(zeta.getId(), "Zeta", "ZETA", "Last",
                workspaceId, timestamp, timestamp));
    }

    @Test
    void getByWorkspaceReturnsEmptyListForExistingWorkspaceWithoutProjects() {
        UUID workspaceId = ACTOR.workspaceId();
        when(projectRepository.findAllInWorkspaceSortedByName(workspaceId)).thenReturn(List.of());

        assertThat(projectService.getByWorkspace(ACTOR, workspaceId)).isEmpty();
    }

    @Test
    void getByWorkspaceOfAnyOtherWorkspaceIsNotFoundWithoutQueryingAnything() {
        UUID otherWorkspaceId = UUID.randomUUID();

        assertThatThrownBy(() -> projectService.getByWorkspace(ACTOR, otherWorkspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Workspace not found: " + otherWorkspaceId);

        verifyNoInteractions(workspaceRepository, projectRepository);
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
        UUID workspaceId = ACTOR.workspaceId();
        Workspace workspace = persistedWorkspace(workspaceId, "Acme Inc.");
        when(workspaceRepository.getReferenceById(workspaceId)).thenReturn(workspace);
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, expectedKey)).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.create(ACTOR, 
                new CreateProjectRequest("Project", rawKey, null));

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
        UUID workspaceId = ACTOR.workspaceId();

        assertThatThrownBy(() -> projectService.create(ACTOR, new CreateProjectRequest("Project", rawKey, null)))
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

        assertThatThrownBy(() -> projectService.create(ACTOR, 
                new CreateProjectRequest("Project", sharpS, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void createRejectsNullKey() {
        assertThatThrownBy(() -> projectService.create(ACTOR, new CreateProjectRequest("Project", null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verifyNoInteractions(workspaceRepository, projectRepository);
    }

    // ---------------------------------------------------------------
    // NAME RULE (create) - same as update(): trim, not blank, max on trimmed
    // ---------------------------------------------------------------

    private Project createAndCaptureSaved(String rawName, String description) {
        UUID workspaceId = ACTOR.workspaceId();
        when(workspaceRepository.getReferenceById(workspaceId))
                .thenReturn(persistedWorkspace(workspaceId, "Acme Inc."));
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, "QF")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectService.create(ACTOR, new CreateProjectRequest(rawName, "qf", description));

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
        assertThatThrownBy(() -> projectService.create(ACTOR, 
                new CreateProjectRequest("a".repeat(256), "qf", null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("255");

        verifyNoInteractions(workspaceRepository, projectRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void createRejectsBlankNameBeforeAnyDatabaseAccess(String blankName) {
        assertThatThrownBy(() -> projectService.create(ACTOR, 
                new CreateProjectRequest(blankName, "qf", null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(workspaceRepository, projectRepository);
    }

    // ---------------------------------------------------------------
    // UPDATE (PATCH)
    // ---------------------------------------------------------------

    private static final OffsetDateTime ORIGINAL_TIMESTAMP = OffsetDateTime.parse("2026-01-01T09:00:00Z");

    /** A persisted-looking project with a fixed past timestamp, found in the caller's workspace. */
    private Project existingProject(UUID projectId, Workspace workspace) {
        Project project = persistedProject(projectId, "Original Name", "ECOM", "Original description", workspace, 8L,
                ORIGINAL_TIMESTAMP);
        when(projectRepository.findByIdAndWorkspaceId(projectId, ACTOR.workspaceId())).thenReturn(Optional.of(project));
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

        ProjectResponse response = projectService.update(ACTOR, projectId, request);

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

        ProjectResponse response = projectService.update(ACTOR, projectId, request);

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

        ProjectResponse response = projectService.update(ACTOR, projectId, request);

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

        ProjectResponse response = projectService.update(ACTOR, projectId, request);

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

        ProjectResponse response = projectService.update(ACTOR, projectId, patch());

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

        projectService.update(ACTOR, projectId, request);

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

        assertThatThrownBy(() -> projectService.update(ACTOR, projectId, request))
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

        assertThatThrownBy(() -> projectService.update(ACTOR, projectId, request))
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

        projectService.update(ACTOR, projectId, request);

        assertThat(project.getName()).hasSize(255);
    }

    @Test
    void updateThrowsResourceNotFoundExceptionForUnknownProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndWorkspaceId(projectId, ACTOR.workspaceId())).thenReturn(Optional.empty());
        UpdateProjectRequest request = patch();
        request.setName("Anything");

        assertThatThrownBy(() -> projectService.update(ACTOR, projectId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(projectId.toString());
    }

    // ---------------------------------------------------------------
    // ROLE POLICY: creating and updating projects is ADMIN-only
    // ---------------------------------------------------------------

    @Test
    void aMemberCannotCreateAProjectAndNothingIsLookedUpOrSaved() {
        assertThatThrownBy(() -> projectService.create(MEMBER, new CreateProjectRequest("Operations", "OPS", null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Only workspace admins can create projects");

        verifyNoInteractions(projectRepository, workspaceRepository);
    }

    /** The role comes first on create: even an invalid key is a 403 for a MEMBER, never a 400. */
    @Test
    void aMemberIsForbiddenBeforeTheRequestIsValidated() {
        assertThatThrownBy(() -> projectService.create(MEMBER, new CreateProjectRequest(" ", "!", null)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void aMemberCannotUpdateAProjectOfTheirOwnWorkspaceAndNothingChanges() {
        Workspace workspace = persistedWorkspace(ACTOR.workspaceId(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = existingProject(projectId, workspace);
        UpdateProjectRequest request = patch();
        request.setName("Renamed");
        request.setDescription(null);

        assertThatThrownBy(() -> projectService.update(MEMBER, projectId, request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Only workspace admins can update projects");

        assertThat(project.getName()).isEqualTo("Original Name");
        assertThat(project.getDescription()).isEqualTo("Original description");
        assertThat(project.getUpdatedAt()).isEqualTo(ORIGINAL_TIMESTAMP);
        assertImmutableFieldsUnchanged(project, workspace);
    }

    /**
     * The project is resolved in the caller's workspace before the role is
     * looked at: a project the caller cannot see is 404 for a MEMBER too,
     * so a 403 never confirms that it exists.
     */
    @Test
    void aProjectOutsideTheCallersWorkspaceIsNotFoundForAMemberToo() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndWorkspaceId(projectId, MEMBER.workspaceId())).thenReturn(Optional.empty());
        UpdateProjectRequest request = patch();
        request.setName("Renamed");

        assertThatThrownBy(() -> projectService.update(MEMBER, projectId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found: " + projectId);
    }

    @Test
    void membersKeepReadAccessToProjects() {
        Workspace workspace = persistedWorkspace(ACTOR.workspaceId(), "Acme Inc.");
        UUID projectId = UUID.randomUUID();
        Project project = persistedProject(projectId, "E-Commerce", "ECOM", null, workspace, 1L, ORIGINAL_TIMESTAMP);
        when(projectRepository.findByIdAndWorkspaceId(projectId, MEMBER.workspaceId()))
                .thenReturn(Optional.of(project));
        when(projectRepository.findByWorkspaceIdAndKey(MEMBER.workspaceId(), "ECOM")).thenReturn(Optional.of(project));
        when(projectRepository.findAllInWorkspaceSortedByName(MEMBER.workspaceId())).thenReturn(List.of(project));

        assertThat(projectService.getById(MEMBER, projectId).id()).isEqualTo(projectId);
        assertThat(projectService.getByKey(MEMBER, "ecom").id()).isEqualTo(projectId);
        assertThat(projectService.getByWorkspace(MEMBER, MEMBER.workspaceId())).hasSize(1);
    }
}
