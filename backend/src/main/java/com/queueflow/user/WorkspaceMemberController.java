package com.queueflow.user;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A workspace's members (its users), addressed as a sub-resource of the
 * workspace. Read-only: inviting, role changes and removal belong to
 * Phase 2. Returns the service's list as-is: ordering (name, then id) and
 * the unknown-workspace check both live in UserService. UserResponse never
 * carries passwordHash.
 */
@Tag(name = "Users")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    private final UserService userService;

    public WorkspaceMemberController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List workspace members", operationId = "listWorkspaceMembers",
            description = "Ordered case-insensitively by name.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping
    public List<UserResponse> getByWorkspace(@PathVariable UUID workspaceId) {
        return userService.getByWorkspace(workspaceId);
    }
}
