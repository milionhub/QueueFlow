package com.queueflow.project;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.PatchField;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.project.dto.UpdateProjectRequest;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

@Service
public class ProjectService {

    /**
     * V1 project key contract, checked on the FINAL normalized value
     * (trim, then upper-case with Locale.ROOT): ASCII A-Z and 0-9 only,
     * 2-10 characters. Checking after normalization matters because
     * upper-casing can change length (e.g. "ß" becomes "SS"), and the
     * result must fit projects.key VARCHAR(10) and stay parseable as the
     * prefix of display keys like "ECOM-7". Java regex character classes
     * are ASCII-only here, so no Unicode letter can pass.
     */
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Z0-9]{2,10}");

    // Matches projects.name VARCHAR(255) - re-checked here (after trimming)
    // because update() can be called without going through bean
    // validation, same reasoning as TicketService's title.
    private static final int NAME_MAX_LENGTH = 255;

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;

    public ProjectService(ProjectRepository projectRepository, WorkspaceRepository workspaceRepository) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        // Validated before any database access, so an invalid key or name can
        // never reach PostgreSQL as a length/constraint failure. The name
        // follows the same rule as update(): trimmed, not blank, max length
        // on the trimmed value. The description is stored as given, as in
        // update().
        String normalizedKey = validatedKey(request.key());
        String name = validatedName(request.name());

        Workspace workspace = workspaceRepository.findById(request.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + request.workspaceId()));

        // Business-level pre-check for a clean error on the normal path.
        // Not the concurrency guarantee - see ProjectRepository's
        // UNIQUE(workspace_id, key) constraint for that.
        if (projectRepository.existsByWorkspaceIdAndKey(request.workspaceId(), normalizedKey)) {
            throw new ResourceAlreadyExistsException(
                    "Project key already exists in workspace: " + normalizedKey);
        }

        Project project = new Project(name, normalizedKey, request.description(), workspace);
        Project saved = projectRepository.save(project);
        return ProjectResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getByWorkspaceAndKey(UUID workspaceId, String key) {
        String normalizedKey = normalizeKey(key);
        Project project = projectRepository.findByWorkspaceIdAndKey(workspaceId, normalizedKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found in workspace " + workspaceId + " with key: " + normalizedKey));
        return ProjectResponse.from(project);
    }

    /**
     * All projects in a workspace, ordered case-insensitively by name
     * (see the repository query for tie-breaking). An unknown
     * workspace is a not-found error, never a silent empty list.
     */
    @Transactional(readOnly = true)
    public List<ProjectResponse> getByWorkspace(UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new ResourceNotFoundException("Workspace not found: " + workspaceId);
        }
        return projectRepository.findAllInWorkspaceSortedByName(workspaceId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /**
     * PATCH-style partial update of name and/or description. The key,
     * workspace and ticket counter are never touched. Every present field
     * is validated before any mutation. No explicit save: the project is a
     * managed entity, so dirty checking flushes real changes at commit; an
     * empty or same-value PATCH changes nothing (not even updatedAt).
     */
    @Transactional
    public ProjectResponse update(UUID projectId, UpdateProjectRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        String newName = request.getName() != null ? validatedName(request.getName()) : null;
        PatchField<String> descriptionPatch = request.descriptionPatch();

        if (newName != null) {
            project.changeName(newName);
        }
        if (descriptionPatch.isPresent()) {
            // Stored as given (explicit null clears it), exactly as create
            // stores the description.
            project.changeDescription(descriptionPatch.value());
        }

        // The entity's change* methods set updatedAt in memory (UTC,
        // microseconds), so the response already carries the exact value
        // that will be persisted.
        return ProjectResponse.from(project);
    }

    private static String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }

    private static String validatedKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessRuleViolationException("key must not be blank");
        }
        String normalizedKey = normalizeKey(key);
        if (!KEY_PATTERN.matcher(normalizedKey).matches()) {
            throw new BusinessRuleViolationException(
                    "key must be 2-10 characters, using only letters A-Z and digits 0-9: " + key);
        }
        return normalizedKey;
    }

    private static String validatedName(String name) {
        if (name == null) {
            throw new BusinessRuleViolationException("name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleViolationException("name must not be blank");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BusinessRuleViolationException("name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
