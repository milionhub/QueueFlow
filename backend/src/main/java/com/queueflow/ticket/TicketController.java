package com.queueflow.ticket;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.ticket.dto.UpdateTicketRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * Thin HTTP adapter over TicketService. Ticket numbering, workspace checks,
 * change detection and Activity recording all live in the service.
 */
@Tag(name = "Tickets", description = "Tickets and their per-project numbering")
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "Create a ticket", operationId = "createTicket",
            description = "Allocates the next ticket number of the project (e.g. CORE-7) and records a "
                    + "TICKET_CREATED activity. The creator is the authenticated user; the project and the "
                    + "optional assignee must belong to the caller's workspace.")
    @ApiResponse(responseCode = "201", description = "Ticket created", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @PostMapping
    public ResponseEntity<TicketResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateTicketRequest request) {
        TicketResponse response = ticketService.create(actor, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{ticketId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get a ticket", operationId = "getTicket")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping("/{ticketId}")
    public TicketResponse getById(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID ticketId) {
        return ticketService.getById(actor, ticketId);
    }

    /**
     * UpdateTicketRequest is deliberately a setter-based class, not a
     * record, so Jackson can tell an omitted property from an explicit JSON
     * null (see PatchField) - it must stay bound directly as the body.
     * Changes are attributed to the authenticated user.
     */
    @Operation(summary = "Update a ticket", operationId = "updateTicket",
            description = "Partial update: omit a field to leave it unchanged; send \"description\": null or "
                    + "\"assigneeId\": null to clear it. Every field that actually changes records one "
                    + "activity; the whole update is applied atomically.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @PatchMapping("/{ticketId}")
    public TicketResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID ticketId,
            @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(actor, ticketId, request);
    }
}
