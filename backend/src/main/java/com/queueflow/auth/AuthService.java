package com.queueflow.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.auth.dto.AuthResponse;
import com.queueflow.auth.dto.LoginRequest;
import com.queueflow.auth.dto.RegisterRequest;
import com.queueflow.common.exception.InvalidCredentialsException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.security.AccessTokenService;
import com.queueflow.user.EmailAddresses;
import com.queueflow.user.User;
import com.queueflow.user.UserAccountRules;
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
        // The same account rules as ADMIN member creation (UserService).
        String name = UserAccountRules.validatedName("name", request.name());
        String email = UserAccountRules.validatedEmail(request.email());
        String password = UserAccountRules.validatedPassword(request.password());
        String workspaceName = UserAccountRules.validatedName("workspaceName", request.workspaceName());

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
}
