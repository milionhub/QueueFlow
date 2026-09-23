package com.queueflow.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.auth.dto.AuthResponse;
import com.queueflow.auth.dto.LoginRequest;
import com.queueflow.auth.dto.RegisterRequest;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.InvalidCredentialsException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.security.AccessTokenService;
import com.queueflow.user.EmailAddresses;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.user.UserRole;
import com.queueflow.user.dto.UserResponse;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * Registration and login. Works on the repositories directly: registration
 * creates a workspace and its first user as one unit, which no single
 * existing service owns.
 */
@Service
public class AuthService {

    // Match workspaces.name / users.name VARCHAR(255) and users.email
    // VARCHAR(320), checked on the normalized values because the service can
    // be called without bean validation, and lower-casing can change length.
    static final int NAME_MAX_LENGTH = 255;
    static final int EMAIL_MAX_LENGTH = 320;
    static final int PASSWORD_MIN_CHARACTERS = 8;
    // BCrypt only uses the first 72 bytes of its input, and Spring Security's
    // encoder refuses anything longer; checked here so it is a 400, never a 500.
    static final int PASSWORD_MAX_UTF8_BYTES = 72;

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;

    /**
     * Compared against when the email is unknown, so that login costs one
     * password check whether or not the account exists (the same approach as
     * Spring Security's DaoAuthenticationProvider). Encoded once, from a
     * random value nobody knows; it can never match.
     */
    private final String unknownUserPasswordHash;

    public AuthService(UserRepository userRepository, WorkspaceRepository workspaceRepository,
            PasswordEncoder passwordEncoder, AccessTokenService accessTokenService) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.unknownUserPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Creates a new workspace and its first user, always as ADMIN, then
     * issues that user's access token. One transaction: any failure after
     * the workspace insert (including a concurrent registration of the same
     * email winning the users unique index) rolls the workspace back too.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String name = validatedName("name", request.name());
        String email = validatedEmail(request.email());
        String password = validatedPassword(request.password());
        String workspaceName = validatedName("workspaceName", request.workspaceName());

        // Business-level pre-check for a clean error on the normal path. Not
        // the concurrency guarantee - that is the users email unique indexes
        // (V1 exact, V5 case-insensitive), surfacing as a 409 via
        // GlobalExceptionHandler.
        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException("Email is already registered");
        }

        Workspace workspace = workspaceRepository.save(new Workspace(workspaceName));
        User user = userRepository.save(
                new User(name, email, passwordEncoder.encode(password), UserRole.ADMIN, workspace));
        return authResponse(user);
    }

    /**
     * Unknown email, wrong password and an unusable stored hash all fail
     * identically, after the same single password check.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(EmailAddresses.normalize(request.email()));
        String encodedPassword = user.map(User::getPasswordHash).orElse(unknownUserPasswordHash);

        boolean passwordMatches = passwordMatches(request.password(), encodedPassword);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return authResponse(user.get());
    }

    private AuthResponse authResponse(User user) {
        return AuthResponse.of(accessTokenService.issue(user.getId()), UserResponse.from(user));
    }

    /**
     * The delegating encoder throws IllegalArgumentException for a stored
     * value it cannot interpret (no {id} prefix, or an unknown id) - e.g. a
     * placeholder hash inserted outside registration. Such an account simply
     * cannot log in; it is not a server error.
     */
    private boolean passwordMatches(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException unusableStoredHash) {
            return false;
        }
    }

    private static String validatedName(String field, String value) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleViolationException(field + " must not be blank");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BusinessRuleViolationException(field + " must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String validatedEmail(String email) {
        String normalized = email == null ? "" : EmailAddresses.normalize(email);
        if (normalized.isEmpty()) {
            throw new BusinessRuleViolationException("email must not be blank");
        }
        if (normalized.length() > EMAIL_MAX_LENGTH) {
            throw new BusinessRuleViolationException(
                    "email must be at most " + EMAIL_MAX_LENGTH + " characters");
        }
        if (!EmailAddresses.isValid(normalized)) {
            throw new BusinessRuleViolationException("email must be a valid email address");
        }
        return normalized;
    }

    /** Returned unchanged: passwords are never trimmed or otherwise normalized. */
    private static String validatedPassword(String password) {
        if (password == null) {
            throw new BusinessRuleViolationException("password is required");
        }
        if (password.codePointCount(0, password.length()) < PASSWORD_MIN_CHARACTERS) {
            throw new BusinessRuleViolationException(
                    "password must be at least " + PASSWORD_MIN_CHARACTERS + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_UTF8_BYTES) {
            throw new BusinessRuleViolationException(
                    "password must be at most " + PASSWORD_MAX_UTF8_BYTES + " bytes when UTF-8 encoded");
        }
        return password;
    }
}
