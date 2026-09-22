package com.queueflow.ticket;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByProjectIdAndTicketNumber(UUID projectId, long ticketNumber);

    boolean existsByProjectIdAndTicketNumber(UUID projectId, long ticketNumber);
}
