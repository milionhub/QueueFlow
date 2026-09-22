package com.queueflow.project;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;

    public ProjectService(ProjectRepository projectRepository, WorkspaceRepository workspaceRepository) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        String normalizedKey = normalizeKey(request.key());

        Workspace workspace = workspaceRepository.findById(request.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + request.workspaceId()));

        // Business-level pre-check for a clean error on the normal path.
        // Not the concurrency guarantee - see ProjectRepository's
        // UNIQUE(workspace_id, key) constraint for that.
        if (projectRepository.existsByWorkspaceIdAndKey(request.workspaceId(), normalizedKey)) {
            throw new ResourceAlreadyExistsException(
                    "Project key already exists in workspace: " + normalizedKey);
        }

        Project project = new Project(request.name(), normalizedKey, request.description(), workspace);
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

    private static String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
