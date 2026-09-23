package com.queueflow.user;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.user.dto.UserResponse;
import com.queueflow.workspace.WorkspaceRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    public UserService(UserRepository userRepository, WorkspaceRepository workspaceRepository) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return UserResponse.from(user);
    }

    /**
     * All members of a workspace, ordered case-insensitively by name
     * (see the repository query for tie-breaking). An unknown
     * workspace is a not-found error, never a silent empty list.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getByWorkspace(UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new ResourceNotFoundException("Workspace not found: " + workspaceId);
        }
        return userRepository.findAllInWorkspaceSortedByName(workspaceId).stream()
                .map(UserResponse::from)
                .toList();
    }
}
