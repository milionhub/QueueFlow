package com.queueflow.comment;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.comment.dto.CommentResponse;
import com.queueflow.comment.dto.CreateCommentRequest;
import com.queueflow.comment.dto.UpdateCommentRequest;
import com.queueflow.config.OpenApiConfig;
import com.queueflow.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * Thin HTTP adapter over CommentService for the Comment resource. The
 * author of a new comment, and the caller of edit/delete, is always the
 * authenticated user; workspace checks and author-only edit/delete live in
 * the service. The per-ticket comment list lives in TicketCommentController.
 */
@Tag(name = "Comments", description = "Comments on tickets")
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "Create a comment", operationId = "createComment",
            description = "The author is the authenticated user, who must belong to the ticket's workspace.")
    @ApiResponse(responseCode = "201", description = "Comment created", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @PostMapping
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.create(actor, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{commentId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get a comment", operationId = "getComment")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/{commentId}")
    public CommentResponse getById(@PathVariable UUID commentId) {
        return commentService.getById(commentId);
    }

    @Operation(summary = "Update a comment", operationId = "updateComment",
            description = "Only the comment's author may edit it.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @PatchMapping("/{commentId}")
    public CommentResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return commentService.update(actor, commentId, request);
    }

    @Operation(summary = "Delete a comment", operationId = "deleteComment",
            description = "Only the comment's author may delete it.")
    @ApiResponse(responseCode = "204", description = "Comment deleted")
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID commentId) {
        commentService.delete(actor, commentId);
        return ResponseEntity.noContent().build();
    }
}
