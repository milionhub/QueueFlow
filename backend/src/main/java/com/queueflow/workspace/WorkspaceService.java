package com.queueflow.workspace;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.workspace.dto.WorkspaceResponse;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    /** Only the caller's own workspace exists for them: any other id is not found, without a lookup. */
    @Transactional(readOnly = true)
    public WorkspaceResponse getById(AuthenticatedUser actor, UUID workspaceId) {
        WorkspaceAccess.requireOwnWorkspace(actor, workspaceId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + workspaceId));
        return WorkspaceResponse.from(workspace);
    }
}
