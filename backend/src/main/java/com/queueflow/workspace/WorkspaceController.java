package com.queueflow.workspace;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.workspace.dto.WorkspaceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only: a workspace is created together with its first (ADMIN) user by
 * POST /api/auth/register, never on its own.
 */
@Tag(name = "Workspaces", description = "Workspaces - the top-level container for members, projects and labels")
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Operation(summary = "Get a workspace", operationId = "getWorkspace")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getById(@PathVariable UUID workspaceId) {
        return workspaceService.getById(workspaceId);
    }
}
