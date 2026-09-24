package com.queueflow.dashboard;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.dashboard.dto.DashboardResponse;
import com.queueflow.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A workspace's dashboard, addressed as a sub-resource of the workspace.
 * Read-only and open to both roles. The own-workspace check, the counting
 * rules and the list limits all live in DashboardService.
 */
@Tag(name = "Dashboard", description = "Read-only workspace overview")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Get the workspace dashboard", operationId = "getWorkspaceDashboard",
            description = "Ticket counts per status, open unassigned tickets, every project with its open and "
                    + "total ticket counts, the caller's open tickets (count, and the 10 most urgent) and the 8 "
                    + "most recently updated tickets. A ticket is open unless its status is DONE.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping
    public DashboardResponse getByWorkspace(@AuthenticationPrincipal AuthenticatedUser actor,
            @Parameter(description = OpenApiConfig.OWN_WORKSPACE_ID) @PathVariable UUID workspaceId) {
        return dashboardService.getByWorkspace(actor, workspaceId);
    }
}
