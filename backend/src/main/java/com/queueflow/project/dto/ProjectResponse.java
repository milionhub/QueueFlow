package com.queueflow.project.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.project.Project;

public record ProjectResponse(
        UUID id,
        String name,
        String key,
        String description,
        UUID workspaceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getKey(),
                project.getDescription(),
                project.getWorkspace().getId(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
