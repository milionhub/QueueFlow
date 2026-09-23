package com.queueflow.ticket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Tenant-scoped (a ticket belongs to its project's workspace): a ticket
     * of another workspace is simply not found.
     */
    Optional<Ticket> findByIdAndProjectWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByIdAndProjectWorkspaceId(UUID id, UUID workspaceId);

    /** Only called for a project already resolved inside the caller's workspace. */
    Optional<Ticket> findByProjectIdAndTicketNumber(UUID projectId, long ticketNumber);

    boolean existsByProjectIdAndTicketNumber(UUID projectId, long ticketNumber);

    /** ticketNumber is unique per project (UNIQUE constraint), so no tie-breaker is needed. */
    List<Ticket> findByProjectIdOrderByTicketNumberAsc(UUID projectId);
}
