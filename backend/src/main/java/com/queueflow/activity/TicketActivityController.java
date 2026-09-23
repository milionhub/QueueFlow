package com.queueflow.activity;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.activity.dto.ActivityResponse;

/**
 * A ticket's activity history, addressed as a sub-resource of the ticket.
 *
 * Deliberately read-only: Activity is an append-only audit trail recorded
 * by the service layer as a side effect of business operations, so there
 * is intentionally no create/update/delete endpoint. Returns the service's
 * list as-is - ordering is the repository's, and rendering type/oldValue/
 * newValue into human-readable text is left to the client.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/activities")
public class TicketActivityController {

    private final ActivityService activityService;

    public TicketActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public List<ActivityResponse> getByTicket(@PathVariable UUID ticketId) {
        return activityService.getByTicket(ticketId);
    }
}
