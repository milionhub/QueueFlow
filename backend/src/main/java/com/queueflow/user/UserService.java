package com.queueflow.user;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.user.dto.UserResponse;
import com.queueflow.workspace.WorkspaceAccess;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
