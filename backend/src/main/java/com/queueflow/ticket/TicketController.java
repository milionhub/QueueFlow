package com.queueflow.ticket;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.queueflow.ticket.dto.CreateTicketRequest;
import com.queueflow.ticket.dto.TicketResponse;
import com.queueflow.ticket.dto.UpdateTicketRequest;

import jakarta.validation.Valid;

/**
 * Thin HTTP adapter over TicketService. Ticket numbering, workspace checks,
 * change detection and Activity recording all live in the service.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketResponse response = ticketService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{ticketId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{ticketId}")
    public TicketResponse getById(@PathVariable UUID ticketId) {
        return ticketService.getById(ticketId);
    }

    @GetMapping("/by-key")
    public TicketResponse getByProjectAndNumber(@RequestParam UUID projectId, @RequestParam long ticketNumber) {
        return ticketService.getByProjectAndNumber(projectId, ticketNumber);
    }

    /**
     * UpdateTicketRequest is deliberately a setter-based class, not a
     * record, so Jackson can tell an omitted property from an explicit JSON
     * null (see PatchField) - it must stay bound directly as the body.
     *
     * TEMPORARY: actorUserId is Phase 1 plumbing only. Phase 2
     * authentication will derive the actor from the authenticated principal
     * and this query parameter will be removed.
     */
    @PatchMapping("/{ticketId}")
    public TicketResponse update(@PathVariable UUID ticketId, @RequestParam UUID actorUserId,
            @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(ticketId, actorUserId, request);
    }
}
