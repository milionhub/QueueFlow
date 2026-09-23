package com.queueflow.user;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.user.dto.UserResponse;

/**
 * Read-only for now: user creation/registration, login, and password
 * changes belong to Phase 2 (authentication). Responses are UserResponse,
 * which never carries passwordHash.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public UserResponse getById(@PathVariable UUID userId) {
        return userService.getById(userId);
    }

    @GetMapping("/by-email")
    public UserResponse getByEmail(@RequestParam String email) {
        return userService.getByEmail(email);
    }
}
