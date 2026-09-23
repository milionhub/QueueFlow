package com.queueflow.auth;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.auth.dto.AuthResponse;
import com.queueflow.auth.dto.LoginRequest;
import com.queueflow.auth.dto.RegisterRequest;
import com.queueflow.config.OpenApiConfig;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.user.UserService;
import com.queueflow.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * Registration and login - the only public API operations, both issuing an
 * access token - and the current user ("me"), whose identity comes solely
 * from that token.
 */
@Tag(name = "Authentication", description = "Registration and login (public, both return a bearer access token), "
        + "and the current user")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @Operation(summary = "Register a new workspace and its first user", operationId = "register",
            description = "Creates a new workspace and a user who becomes its ADMIN, then returns an access "
                    + "token for that user. The email is trimmed and lower-cased; it must not already be "
                    + "registered in any letter case.")
    @SecurityRequirements // public: no access token required
    @ApiResponse(responseCode = "201", description = "Registered", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/users/{userId}")
                .buildAndExpand(response.user().id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Log in", operationId = "login",
            description = "Returns a new access token. An unknown email and a wrong password get the same "
                    + "401 response.")
    @SecurityRequirements // public: no access token required
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * The caller is whoever the access token identifies - there is no
     * parameter to choose another user. The authentication step only loaded
     * the user's id, workspace and role, so the full profile is read here
     * (one primary-key query, and only for this endpoint).
     */
    @Operation(summary = "Get the current user", operationId = "getCurrentUser",
            description = "The user identified by the access token.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userService.getById(currentUser, currentUser.userId());
    }
}
