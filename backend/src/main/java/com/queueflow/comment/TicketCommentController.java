package com.queueflow.comment;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.comment.dto.CommentResponse;

/**
 * A ticket's comments, addressed as a sub-resource of the ticket. Separate
 * from CommentController only so each controller keeps a single base path
 * (same split as TicketLabelController). Returns the service's list as-is:
 * ordering (chronological, oldest first) is the repository's, not the
 * controller's.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class TicketCommentController {

    private final CommentService commentService;

    public TicketCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public List<CommentResponse> getByTicket(@PathVariable UUID ticketId) {
        return commentService.getByTicket(ticketId);
    }
}
