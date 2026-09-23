package com.queueflow.workspace;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.workspace.dto.CreateWorkspaceRequest;
import com.queueflow.workspace.dto.WorkspaceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "Workspaces", description = "Workspaces - the top-level container for members, projects and labels")
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * @Valid is load-bearing here, not decorative: WorkspaceService.create
     * deliberately relies on CreateWorkspaceRequest's bean-validation
     * constraints (not blank, max 255) rather than re-checking them.
     */
    @Operation(summary = "Create a workspace", operationId = "createWorkspace")
    @ApiResponse(responseCode = "201", description = "Workspace created", useReturnTypeSchema = true)
    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceResponse response = workspaceService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{workspaceId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get a workspace", operationId = "getWorkspace")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getById(@PathVariable UUID workspaceId) {
        return workspaceService.getById(workspaceId);
    }
}
