package com.queueflow.project;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.project.dto.ProjectResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A workspace's projects, addressed as a sub-resource of the workspace.
 * Returns the service's list as-is: ordering (name, then id) and the
 * unknown-workspace check both live in ProjectService.
 */
@Tag(name = "Projects")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
public class WorkspaceProjectController {

    private final ProjectService projectService;

    public WorkspaceProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "List workspace projects", operationId = "listWorkspaceProjects",
            description = "Ordered case-insensitively by name.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping
    public List<ProjectResponse> getByWorkspace(@PathVariable UUID workspaceId) {
        return projectService.getByWorkspace(workspaceId);
    }
}
