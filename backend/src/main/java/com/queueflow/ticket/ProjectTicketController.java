package com.queueflow.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.ticket.dto.TicketResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A project's tickets, addressed as a sub-resource of the project:
 * the collection, and one ticket by its per-project number (e.g. 7 in
 * "ECOM-7" - the number only, not a display key; tickets by UUID stay at
 * GET /api/tickets/{ticketId}). Returns the service's results as-is:
 * ordering (ticket number) and the not-found checks live in TicketService.
 * No filtering, search or board grouping - the client groups by status.
 */
@Tag(name = "Tickets")
@RestController
@RequestMapping("/api/projects/{projectId}/tickets")
public class ProjectTicketController {

    private final TicketService ticketService;

    public ProjectTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "List project tickets", operationId = "listProjectTickets",
            description = "Ordered by ticket number.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping
    public List<TicketResponse> getByProject(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID projectId) {
        return ticketService.getByProject(actor, projectId);
    }

    @Operation(summary = "Get a ticket by project and number", operationId = "getTicketByNumber")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/{ticketNumber}")
    public TicketResponse getByNumber(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID projectId,
            @Parameter(description = "Per-project ticket number, e.g. 7 for CORE-7") @PathVariable long ticketNumber) {
        return ticketService.getByProjectAndNumber(actor, projectId, ticketNumber);
    }
}
