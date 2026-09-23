package com.queueflow.project.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.project.Project;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectResponse(
        UUID id,
        String name,
        String key,
        @Schema(types = {"string", "null"})
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
