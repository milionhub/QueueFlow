package com.queueflow.label;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.ticket.dto.TicketResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Ticket-label associations, addressed as a sub-resource of the ticket.
 * Separate from LabelController only so each controller keeps a single
 * base path; both delegate to LabelService, which owns workspace checks,
 * idempotency and LABEL_ADDED/LABEL_REMOVED Activity recording.
 *
 * PUT and DELETE are both idempotent by design: re-adding an attached
 * label or removing an absent one succeeds without changing anything.
 * Both return the service's TicketResponse (which does not list labels -
 * see Phase 1.7G).
 *
 * TEMPORARY: actorUserId is Phase 1 plumbing only. Phase 2 authentication
 * will derive the actor from the authenticated principal and this query
 * parameter will be removed.
 */
@Tag(name = "Labels")
@RestController
@RequestMapping("/api/tickets/{ticketId}/labels")
public class TicketLabelController {

    private final LabelService labelService;

    public TicketLabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @Operation(summary = "Add a label to a ticket", operationId = "addTicketLabel",
            description = "Idempotent: adding a label that is already attached changes nothing and records no "
                    + "activity. Returns the updated ticket.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    @PutMapping("/{labelId}")
    public TicketResponse addLabel(@PathVariable UUID ticketId, @PathVariable UUID labelId,
            @Parameter(description = OpenApiConfig.ACTOR_USER_ID) @RequestParam UUID actorUserId) {
        return labelService.addLabelToTicket(ticketId, labelId, actorUserId);
    }

    @Operation(summary = "Remove a label from a ticket", operationId = "removeTicketLabel",
            description = "Idempotent: removing a label that is not attached changes nothing and records no "
                    + "activity. Returns the updated ticket.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @DeleteMapping("/{labelId}")
    public TicketResponse removeLabel(@PathVariable UUID ticketId, @PathVariable UUID labelId,
            @Parameter(description = OpenApiConfig.ACTOR_USER_ID) @RequestParam UUID actorUserId) {
        return labelService.removeLabelFromTicket(ticketId, labelId, actorUserId);
    }
}
