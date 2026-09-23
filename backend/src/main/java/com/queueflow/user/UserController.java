package com.queueflow.user;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only for now: user creation/registration, login, and password
 * changes belong to Phase 2 (authentication). Responses are UserResponse,
 * which never carries passwordHash.
 */
@Tag(name = "Users", description = "Users and workspace members (read-only in Phase 1)")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get a user", operationId = "getUser")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/{userId}")
    public UserResponse getById(@PathVariable UUID userId) {
        return userService.getById(userId);
    }

    @Operation(summary = "Get a user by email", operationId = "getUserByEmail")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/by-email")
    public UserResponse getByEmail(@Parameter(description = "Exact email address") @RequestParam String email) {
        return userService.getByEmail(email);
    }
}
