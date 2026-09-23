package com.queueflow.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.ticket.dto.TicketResponse;

/**
 * A project's tickets, addressed as a sub-resource of the project:
 * the collection, and one ticket by its per-project number (e.g. 7 in
 * "ECOM-7" - the number only, not a display key; tickets by UUID stay at
 * GET /api/tickets/{ticketId}). Returns the service's results as-is:
 * ordering (ticket number) and the not-found checks live in TicketService.
 * No filtering, search or board grouping - the client groups by status.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/tickets")
public class ProjectTicketController {

    private final TicketService ticketService;

    public ProjectTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<TicketResponse> getByProject(@PathVariable UUID projectId) {
        return ticketService.getByProject(projectId);
    }

    @GetMapping("/{ticketNumber}")
    public TicketResponse getByNumber(@PathVariable UUID projectId, @PathVariable long ticketNumber) {
        return ticketService.getByProjectAndNumber(projectId, ticketNumber);
    }
}
