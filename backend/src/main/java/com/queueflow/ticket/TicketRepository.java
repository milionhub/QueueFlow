package com.queueflow.ticket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByProjectIdAndTicketNumber(UUID projectId, long ticketNumber);

    boolean existsByProjectIdAndTicketNumber(UUID projectId, long ticketNumber);

    /** ticketNumber is unique per project (UNIQUE constraint), so no tie-breaker is needed. */
    List<Ticket> findByProjectIdOrderByTicketNumberAsc(UUID projectId);
}
