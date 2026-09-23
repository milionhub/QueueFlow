package com.queueflow.project;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.project.dto.ProjectResponse;

/**
 * A workspace's projects, addressed as a sub-resource of the workspace.
 * Returns the service's list as-is: ordering (name, then id) and the
 * unknown-workspace check both live in ProjectService.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
public class WorkspaceProjectController {

    private final ProjectService projectService;

    public WorkspaceProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> getByWorkspace(@PathVariable UUID workspaceId) {
        return projectService.getByWorkspace(workspaceId);
    }
}
