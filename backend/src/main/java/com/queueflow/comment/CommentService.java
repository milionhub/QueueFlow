package com.queueflow.comment;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.comment.dto.CommentResponse;
import com.queueflow.comment.dto.CreateCommentRequest;
import com.queueflow.comment.dto.UpdateCommentRequest;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ForbiddenOperationException;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, TicketRepository ticketRepository,
            UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    /** The author is the acting user - never a client-supplied id. */
    @Transactional
    public CommentResponse create(AuthenticatedUser actor, CreateCommentRequest request) {
        // Only a ticket of the caller's workspace can be commented on.
        Ticket ticket = ticketRepository.findByIdAndProjectWorkspaceId(request.ticketId(), actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + request.ticketId()));
        // A reference, no query: the user was loaded to authenticate this request.
        User author = userRepository.getReferenceById(actor.userId());

        String content = validatedContent(request.content());

        // Deliberately not touching Ticket at all here: adding a comment is
        // not a Ticket modification, so ticket.updatedAt must not change.
        Comment comment = new Comment(content, ticket, author);
        Comment saved = commentRepository.save(comment);
        return CommentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CommentResponse getById(AuthenticatedUser actor, UUID commentId) {
        return CommentResponse.from(visibleComment(actor, commentId));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getByTicket(AuthenticatedUser actor, UUID ticketId) {
        if (!ticketRepository.existsByIdAndProjectWorkspaceId(ticketId, actor.workspaceId())) {
            throw new ResourceNotFoundException("Ticket not found: " + ticketId);
        }
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    /**
     * A comment outside the caller's workspace is not found (404). Inside it,
     * only the author may edit - whatever their role (403).
     */
    @Transactional
    public CommentResponse update(AuthenticatedUser actor, UUID commentId, UpdateCommentRequest request) {
        Comment comment = visibleComment(actor, commentId);

        if (!comment.getAuthor().getId().equals(actor.userId())) {
            throw new ForbiddenOperationException("Only the comment author can edit this comment");
        }

        comment.changeContent(validatedContent(request.content()));

        // No explicit commentRepository.save(comment): comment is a managed
        // entity in this transaction's persistence context, so Hibernate's
        // dirty checking detects the change. The explicit flush() (not a
        // save) issues that UPDATE now rather than at commit, so @PreUpdate
        // has already refreshed updatedAt before the response is built -
        // same reasoning as TicketService.update().
        commentRepository.flush();
        return CommentResponse.from(comment);
    }

    /** Same order as update: not found outside the workspace, then author-only. */
    @Transactional
    public void delete(AuthenticatedUser actor, UUID commentId) {
        Comment comment = visibleComment(actor, commentId);

        if (!comment.getAuthor().getId().equals(actor.userId())) {
            throw new ForbiddenOperationException("Only the comment author can delete this comment");
        }

        commentRepository.delete(comment);
    }

    /** A comment of the caller's workspace; any other is not found. */
    private Comment visibleComment(AuthenticatedUser actor, UUID commentId) {
        return commentRepository.findByIdAndTicketProjectWorkspaceId(commentId, actor.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
    }

    private static String validatedContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessRuleViolationException("content must not be blank");
        }
        return content;
    }
}
