package com.queueflow.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.ticket.dto.TicketResponse;

/**
 * A project's tickets, addressed as a sub-resource of the project. Returns
 * the service's list as-is: ordering (ticket number) and the
 * unknown-project check both live in TicketService. No filtering, search
 * or board grouping - the client groups by status.
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
}
