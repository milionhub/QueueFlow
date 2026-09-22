package com.queueflow.comment.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.comment.Comment;

public record CommentResponse(
        UUID id,
        String content,
        UUID ticketId,
        UUID authorId,
        String authorName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * Reads comment.getAuthor().getName(), which - unlike a lazy proxy's
     * id - is a real column and initializes the association if not already
     * loaded. Must be called inside the transactional service boundary
     * that loaded/created the Comment, same as Ticket.getDisplayKey().
     */
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
