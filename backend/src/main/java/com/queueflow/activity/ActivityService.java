package com.queueflow.activity;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.activity.dto.ActivityResponse;
import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketRepository;
import com.queueflow.user.User;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final TicketRepository ticketRepository;

    public ActivityService(ActivityRepository activityRepository, TicketRepository ticketRepository) {
        this.activityRepository = activityRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getByTicket(AuthenticatedUser actor, UUID ticketId) {
        if (!ticketRepository.existsByIdAndProjectWorkspaceId(ticketId, actor.workspaceId())) {
            throw new ResourceNotFoundException("Ticket not found: " + ticketId);
        }
        return activityRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream()
                .map(ActivityResponse::from)
                .toList();
    }

    /**
     * Internal recording hook for other services to append an Activity as
     * part of their own business operation. Deliberately not a REST-shaped
     * "create" endpoint: it takes already-loaded Ticket/User entities
     * rather than raw ids from a request body, so only code that already
     * holds those entities within an active transaction can call it - there
     * is no DTO or controller through which an arbitrary client could reach
     * this. Plain @Transactional (default REQUIRED propagation) means it
     * joins whatever transaction is already open on the calling thread
     * rather than starting a new one, so an Activity persistence failure
     * rolls back the caller's whole business operation along with it - no
     * self-invocation trick and no REQUIRES_NEW involved, since this is a
     * genuinely different Spring bean being called through its own proxy.
     */
    @Transactional
    public void recordActivity(ActivityType type, String oldValue, String newValue, Ticket ticket, User user) {
        activityRepository.save(new Activity(type, oldValue, newValue, ticket, user));
    }
}
