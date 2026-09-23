package com.queueflow.comment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Tenant-scoped (a comment belongs to its ticket's project's workspace):
     * a comment of another workspace is simply not found.
     */
    Optional<Comment> findByIdAndTicketProjectWorkspaceId(UUID id, UUID workspaceId);

    /** Only called for a ticket already resolved inside the caller's workspace. */
    List<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
