package com.queueflow.label;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.config.OpenApiConfig;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.ticket.dto.TicketResponse;

import io.swagger.v3.oas.annotations.Operation;
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
 * Both return the updated TicketResponse, including its labels. The
 * activity they record is attributed to the authenticated user.
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
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    @PutMapping("/{labelId}")
    public TicketResponse addLabel(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID ticketId,
            @PathVariable UUID labelId) {
        return labelService.addLabelToTicket(actor, ticketId, labelId);
    }

    @Operation(summary = "Remove a label from a ticket", operationId = "removeTicketLabel",
            description = "Idempotent: removing a label that is not attached changes nothing and records no "
                    + "activity. Returns the updated ticket.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @DeleteMapping("/{labelId}")
    public TicketResponse removeLabel(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID ticketId, @PathVariable UUID labelId) {
        return labelService.removeLabelFromTicket(actor, ticketId, labelId);
    }
}
