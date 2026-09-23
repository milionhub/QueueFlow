package com.queueflow.comment;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.comment.dto.CommentResponse;
import com.queueflow.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A ticket's comments, addressed as a sub-resource of the ticket. Separate
 * from CommentController only so each controller keeps a single base path
 * (same split as TicketLabelController). Returns the service's list as-is:
 * ordering (chronological, oldest first) is the repository's, not the
 * controller's.
 */
@Tag(name = "Comments")
@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class TicketCommentController {

    private final CommentService commentService;

    public TicketCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "List ticket comments", operationId = "listTicketComments",
            description = "Oldest first.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping
    public List<CommentResponse> getByTicket(@PathVariable UUID ticketId) {
        return commentService.getByTicket(ticketId);
    }
}
