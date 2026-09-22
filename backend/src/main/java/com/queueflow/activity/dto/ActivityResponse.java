package com.queueflow.activity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.activity.Activity;
import com.queueflow.activity.ActivityType;

public record ActivityResponse(
        UUID id,
        ActivityType type,
        String oldValue,
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
