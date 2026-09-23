package com.queueflow.comment;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.comment.dto.CommentResponse;
import com.queueflow.comment.dto.CreateCommentRequest;
import com.queueflow.comment.dto.UpdateCommentRequest;

import jakarta.validation.Valid;

/**
 * Thin HTTP adapter over CommentService for the Comment resource. Author
 * workspace checks and author-only edit/delete live in the service. The
 * per-ticket comment list lives in TicketCommentController.
 *
 * TEMPORARY (Phase 1 plumbing, removed by Phase 2 authentication, which
 * will derive the caller from the authenticated principal):
 * - create trusts the client-supplied authorId in CreateCommentRequest;
 * - update/delete take the caller as the actorUserId query parameter.
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<CommentResponse> create(@Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{commentId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{commentId}")
    public CommentResponse getById(@PathVariable UUID commentId) {
        return commentService.getById(commentId);
    }

    @PatchMapping("/{commentId}")
    public CommentResponse update(@PathVariable UUID commentId, @RequestParam UUID actorUserId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return commentService.update(commentId, actorUserId, request);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID commentId, @RequestParam UUID actorUserId) {
        commentService.delete(commentId, actorUserId);
        return ResponseEntity.noContent().build();
    }
}
