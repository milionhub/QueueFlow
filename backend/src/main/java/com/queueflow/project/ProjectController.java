package com.queueflow.project;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.project.dto.CreateProjectRequest;
import com.queueflow.project.dto.ProjectResponse;
import com.queueflow.project.dto.UpdateProjectRequest;
import com.queueflow.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * Thin HTTP adapter over ProjectService. Key normalization, workspace
 * existence and duplicate-key checks all live in the service - the raw
 * key is passed through untouched on both create and lookup.
 */
@Tag(name = "Projects", description = "Projects inside a workspace")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "Create a project", operationId = "createProject",
            description = "Created in the caller's workspace. The key is trimmed and upper-cased, must then be "
                    + "2-10 characters of A-Z and 0-9, is unique within the workspace and cannot be changed later.")
    @ApiResponse(responseCode = "201", description = "Project created", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.create(actor, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{projectId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get a project", operationId = "getProject")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/{projectId}")
    public ProjectResponse getById(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID projectId) {
        return projectService.getById(actor, projectId);
    }

    /**
     * Partial update of name and/or description only - the key is
     * immutable. UpdateProjectRequest is a setter-based class (not a
     * record) so Jackson keeps an omitted description distinct from an
     * explicit null. Any member of the project's workspace may currently
     * edit it (role rules are not applied yet).
     */
    @Operation(summary = "Update a project", operationId = "updateProject",
            description = "Partial update of name and description only; the key is immutable. Omit a field to leave "
                    + "it unchanged; send \"description\": null to clear the description.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @PatchMapping("/{projectId}")
    public ProjectResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(actor, projectId, request);
    }

    @Operation(summary = "Get a project by key", operationId = "getProjectByKey",
            description = "Looks the key up in the caller's workspace.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/by-key")
    public ProjectResponse getByKey(@AuthenticationPrincipal AuthenticatedUser actor,
            @Parameter(description = "Project key; trimmed and upper-cased before lookup") @RequestParam String key) {
        return projectService.getByKey(actor, key);
    }
}
