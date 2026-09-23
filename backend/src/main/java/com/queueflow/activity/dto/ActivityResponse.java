package com.queueflow.activity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.activity.Activity;
import com.queueflow.activity.ActivityType;

import io.swagger.v3.oas.annotations.media.Schema;

public record ActivityResponse(
        UUID id,
        ActivityType type,
        @Schema(types = {"string", "null"},
                description = "Previous value; null when not applicable or when the value was empty")
        String oldValue,
        @Schema(types = {"string", "null"},
                description = "New value; null when not applicable or when the value was cleared")
        String newValue,
        UUID ticketId,
        UUID userId,
        String userName,
        OffsetDateTime createdAt) {

    /**
     * Reads activity.getUser().getName(), which - unlike a lazy proxy's id
     * - is a real column and initializes the association if not already
     * loaded. Must be called inside the transactional service boundary
     * that loaded the Activity, same as CommentResponse.authorName.
     */
    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getOldValue(),
                activity.getNewValue(),
                activity.getTicket().getId(),
                activity.getUser().getId(),
                activity.getUser().getName(),
                activity.getCreatedAt());
    }
}
