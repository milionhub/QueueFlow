package com.queueflow.auth;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.auth.dto.AuthResponse;
import com.queueflow.auth.dto.LoginRequest;
import com.queueflow.auth.dto.RegisterRequest;
import com.queueflow.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * Registration and login: the two public entry points that issue access
 * tokens. Both are thin adapters over AuthService.
 *
 * Phase 2.3 state: tokens are issued, but no other endpoint requires or
 * reads one yet - authentication of /api/** is turned on in a later step.
 */
@Tag(name = "Authentication", description = "Registration and login; both return a bearer access token")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new workspace and its first user", operationId = "register",
            description = "Creates a new workspace and a user who becomes its ADMIN, then returns an access "
                    + "token for that user. The email is trimmed and lower-cased; it must not already be "
                    + "registered in any letter case.")
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
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
