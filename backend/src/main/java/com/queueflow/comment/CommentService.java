package com.queueflow.comment;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.comment.dto.CommentResponse;
import com.queueflow.comment.dto.CreateCommentRequest;
import com.queueflow.comment.dto.UpdateCommentRequest;
import com.queueflow.common.exception.BusinessRuleViolationException;
import com.queueflow.common.exception.ResourceNotFoundException;
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

    @Transactional
    public CommentResponse create(CreateCommentRequest request) {
        Ticket ticket = ticketRepository.findById(request.ticketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + request.ticketId()));
        User author = userRepository.findById(request.authorId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.authorId()));

        UUID ticketWorkspaceId = ticket.getProject().getWorkspace().getId();
        UUID authorWorkspaceId = author.getWorkspace().getId();
        if (!ticketWorkspaceId.equals(authorWorkspaceId)) {
            throw new BusinessRuleViolationException("Comment author must belong to the same workspace as the ticket");
        }

        String content = validatedContent(request.content());

        // Deliberately not touching Ticket at all here: adding a comment is
        // not a Ticket modification, so ticket.updatedAt must not change.
        Comment comment = new Comment(content, ticket, author);
        Comment saved = commentRepository.save(comment);
        return CommentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CommentResponse getById(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getByTicket(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket not found: " + ticketId);
        }
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse update(UUID commentId, UUID actorUserId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!comment.getAuthor().getId().equals(actorUserId)) {
            throw new BusinessRuleViolationException("Only the comment author can edit this comment");
        }

        comment.changeContent(validatedContent(request.content()));

        // No explicit commentRepository.save(comment): comment is a managed
        // entity in this transaction's persistence context, so Hibernate's
        // dirty checking flushes the change (firing @PreUpdate to refresh
        // updatedAt) automatically at commit.
        return CommentResponse.from(comment);
    }

    @Transactional
    public void delete(UUID commentId, UUID actorUserId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!comment.getAuthor().getId().equals(actorUserId)) {
            throw new BusinessRuleViolationException("Only the comment author can delete this comment");
        }

        commentRepository.delete(comment);
    }

    private static String validatedContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessRuleViolationException("content must not be blank");
        }
        return content;
    }
}
