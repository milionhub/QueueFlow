package com.queueflow.workspace.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.workspace.Workspace;

public record WorkspaceResponse(
        UUID id,
        String name,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }
}
