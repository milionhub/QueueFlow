package com.queueflow.user;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceAlreadyExistsException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.security.RoleAccess;
import com.queueflow.ticket.TicketService;
import com.queueflow.user.dto.CreateMemberRequest;
import com.queueflow.user.dto.UpdateMemberRequest;
import com.queueflow.user.dto.UserResponse;
import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceAccess;
import com.queueflow.workspace.WorkspaceRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final TicketService ticketService;

    public UserService(UserRepository userRepository, WorkspaceRepository workspaceRepository,
            PasswordEncoder passwordEncoder, TicketService ticketService) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.ticketService = ticketService;
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

    /**
     * An ADMIN renames a user of their own workspace. Only the name can
     * change (UpdateMemberRequest has nothing else), under the same rules
     * as registration and member creation.
     *
     * Same order as every operation on an existing resource: another
     * workspace, and a user not (or no longer) in this one, are not found
     * (404) before the role is looked at, so a MEMBER's 403 only ever
     * concerns a real member of their own workspace.
     */
    @Transactional
    public UserResponse updateMember(AuthenticatedUser actor, UUID workspaceId, UUID userId,
            UpdateMemberRequest request) {
        User member = memberOfOwnWorkspace(actor, workspaceId, userId);
        RoleAccess.requireAdmin(actor, "edit members");

        member.rename(UserAccountRules.validatedName("name", request.name()));
        return UserResponse.from(member);
    }

    /**
     * An ADMIN removes a MEMBER from their own workspace. Same 404-then-403
     * order as updateMember; then only a MEMBER other than the caller can be
     * removed (V1 has no way to hand over a workspace, so its ADMIN stays).
     *
     * The user's row is kept and marked removed (User.markRemoved, see V6):
     * the tickets they created, their comments and the activity they caused
     * keep pointing at it, under their name. From then on they are not a
     * member: not listed, not assignable, and neither their password nor a
     * token issued before the removal is accepted (UserRepository). Tickets
     * assigned to them become unassigned, each with its activity entry.
     * One transaction: nothing of this is half done.
     */
    @Transactional
    public void removeMember(AuthenticatedUser actor, UUID workspaceId, UUID userId) {
        User member = memberOfOwnWorkspace(actor, workspaceId, userId);
        RoleAccess.requireAdmin(actor, "remove members");
        if (member.getId().equals(actor.userId())) {
            throw new BusinessRuleViolationException("You cannot remove yourself from the workspace");
        }
        if (member.getRole() != UserRole.MEMBER) {
            throw new BusinessRuleViolationException("Only members can be removed, not admins");
        }

        ticketService.unassignRemovedMember(actor, member);
        member.markRemoved();
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

    /** The own-workspace check, then a current member of it; anything else is not found. */
    private User memberOfOwnWorkspace(AuthenticatedUser actor, UUID workspaceId, UUID userId) {
        WorkspaceAccess.requireOwnWorkspace(actor, workspaceId);
        return userRepository.findByIdAndWorkspaceId(userId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
