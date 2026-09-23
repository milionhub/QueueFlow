package com.queueflow.label;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.label.dto.LabelResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A workspace's labels, addressed as a sub-resource of the workspace.
 * Returns the service's list as-is: ordering (name, then id) and the
 * unknown-workspace check both live in LabelService.
 */
@Tag(name = "Labels")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/labels")
public class WorkspaceLabelController {

    private final LabelService labelService;

    public WorkspaceLabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @Operation(summary = "List workspace labels", operationId = "listWorkspaceLabels",
            description = "Ordered case-insensitively by name.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping
    public List<LabelResponse> getByWorkspace(@PathVariable UUID workspaceId) {
        return labelService.getByWorkspace(workspaceId);
    }
}
