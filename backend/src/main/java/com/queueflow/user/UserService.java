package com.queueflow.user;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.security.RoleAccess;
import com.queueflow.user.dto.CreateMemberRequest;
import com.queueflow.user.dto.UserResponse;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceAccess;
import com.queueflow.workspace.WorkspaceRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, WorkspaceRepository workspaceRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * An ADMIN adds a member to their own workspace. The new user is always
     * a MEMBER of the caller's workspace - neither can be chosen by the
     * request - and gets no token: they log in with POST /api/auth/login.
     *
     * Order matters: another workspace is not found (404) before the role is
     * looked at, so the 403 for a MEMBER only ever concerns their own
     * workspace. Then the same account rules as registration
     * (UserAccountRules) and the same global email uniqueness.
     */
    @Transactional
    public UserResponse createMember(AuthenticatedUser actor, UUID workspaceId, CreateMemberRequest request) {
        WorkspaceAccess.requireOwnWorkspace(actor, workspaceId);
        RoleAccess.requireAdmin(actor, "create members");

        String name = UserAccountRules.validatedName("name", request.name());
        String email = UserAccountRules.validatedEmail(request.email());
        String password = UserAccountRules.validatedPassword(request.password());

        // Emails are unique across QueueFlow, so this answers 409 for an
        // address used in any workspace - with the same message as
        // registration, which says nothing about where. Not the concurrency
        // guarantee: that is the V1/V5 unique indexes on users.email,
        // surfacing as a 409 via GlobalExceptionHandler.
        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException("Email is already registered");
        }
        // A reference, no query: the caller's workspace was loaded to authenticate them.
        Workspace workspace = workspaceRepository.getReferenceById(actor.workspaceId());
        User member = userRepository.save(
                new User(name, email, passwordEncoder.encode(password), UserRole.MEMBER, workspace));
        return UserResponse.from(member);
    }

    /** Users of other workspaces are not found. */
    @Transactional(readOnly = true)
    public UserResponse getById(AuthenticatedUser actor, UUID userId) {
        User user = userRepository.findByIdAndWorkspaceId(userId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return UserResponse.from(user);
    }

    /**
     * Only searches the caller's workspace: emails are unique across
     * QueueFlow, but this must not answer whether an address is registered
     * anywhere else.
     */
    @Transactional(readOnly = true)
    public UserResponse getByEmail(AuthenticatedUser actor, String email) {
        User user = userRepository.findByEmailAndWorkspaceId(email, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return UserResponse.from(user);
    }

    /**
     * All members of a workspace, ordered case-insensitively by name
     * (see the repository query for tie-breaking). Any workspace other than
     * the caller's is not found, never a silent empty list.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getByWorkspace(AuthenticatedUser actor, UUID workspaceId) {
        WorkspaceAccess.requireOwnWorkspace(actor, workspaceId);
        return userRepository.findAllInWorkspaceSortedByName(actor.workspaceId()).stream()
                .map(UserResponse::from)
                .toList();
    }
}
