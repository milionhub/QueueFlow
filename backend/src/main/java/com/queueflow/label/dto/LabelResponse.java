package com.queueflow.label.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.label.Label;

public record LabelResponse(
        UUID id,
        String name,
        UUID workspaceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static LabelResponse from(Label label) {
        return new LabelResponse(
                label.getId(),
                label.getName(),
                label.getWorkspace().getId(),
                label.getCreatedAt(),
                label.getUpdatedAt());
    }
}
