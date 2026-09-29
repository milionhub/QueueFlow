package com.queueflow.user;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.user.dto.CreateMemberRequest;
import com.queueflow.user.dto.UpdateMemberRequest;
import com.queueflow.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * A workspace's members (its users), addressed as a sub-resource of the
 * workspace: listing them, and an ADMIN adding, renaming or removing one.
 * The own-workspace check, the ADMIN rule, ordering (name, then id), the
 * account rules and what removal keeps all live in UserService.
 * UserResponse never carries passwordHash.
 */
@Tag(name = "Users")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    private final UserService userService;

    public WorkspaceMemberController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Create a member", operationId = "createWorkspaceMember",
            description = "ADMIN only. Adds a user to the caller's own workspace, always with role MEMBER: the "
                    + "request cannot choose a role or workspace. Emails are unique across QueueFlow. No token is "
                    + "issued; the new member logs in with POST /api/auth/login.")
    @ApiResponse(responseCode = "201", description = "Member created", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    @PostMapping
    public ResponseEntity<UserResponse> createMember(@AuthenticationPrincipal AuthenticatedUser actor,
            @Parameter(description = OpenApiConfig.OWN_WORKSPACE_ID) @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateMemberRequest request) {
        UserResponse response = userService.createMember(actor, workspaceId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/users/{userId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "List workspace members", operationId = "listWorkspaceMembers",
            description = "Ordered case-insensitively by name.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping
    public List<UserResponse> getByWorkspace(@AuthenticationPrincipal AuthenticatedUser actor,
            @Parameter(description = OpenApiConfig.OWN_WORKSPACE_ID) @PathVariable UUID workspaceId) {
        return userService.getByWorkspace(actor, workspaceId);
    }

    @Operation(summary = "Edit a member", operationId = "updateWorkspaceMember",
            description = "ADMIN only. Changes a member's name - nothing else can be changed here: email, password, "
                    + "role and workspace are not part of the request.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @PatchMapping("/{userId}")
    public UserResponse updateMember(@AuthenticationPrincipal AuthenticatedUser actor,
            @Parameter(description = OpenApiConfig.OWN_WORKSPACE_ID) @PathVariable UUID workspaceId,
            @PathVariable UUID userId, @Valid @RequestBody UpdateMemberRequest request) {
        return userService.updateMember(actor, workspaceId, userId, request);
    }

    @Operation(summary = "Remove a member", operationId = "removeWorkspaceMember",
            description = "ADMIN only, for a MEMBER other than the caller. The member can no longer log in, and "
                    + "tokens already issued to them stop working. Tickets assigned to them become unassigned; "
                    + "the tickets they created, their comments and their activity stay.")
    @ApiResponse(responseCode = "204", description = "Member removed")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthenticatedUser actor,
            @Parameter(description = OpenApiConfig.OWN_WORKSPACE_ID) @PathVariable UUID workspaceId,
            @PathVariable UUID userId) {
        userService.removeMember(actor, workspaceId, userId);
        return ResponseEntity.noContent().build();
    }
}
