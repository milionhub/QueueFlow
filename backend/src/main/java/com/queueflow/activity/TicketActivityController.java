package com.queueflow.activity;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueflow.activity.dto.ActivityResponse;
import com.queueflow.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A ticket's activity history, addressed as a sub-resource of the ticket.
 *
 * Deliberately read-only: Activity is an append-only audit trail recorded
 * by the service layer as a side effect of business operations, so there
 * is intentionally no create/update/delete endpoint. Returns the service's
 * list as-is - ordering is the repository's, and rendering type/oldValue/
 * newValue into human-readable text is left to the client.
 */
@Tag(name = "Activity", description = "Read-only ticket activity history")
@RestController
@RequestMapping("/api/tickets/{ticketId}/activities")
public class TicketActivityController {

    private final ActivityService activityService;

    public TicketActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Operation(summary = "List ticket activity", operationId = "listTicketActivity",
            description = "History recorded automatically by ticket and label changes; oldest first. Read-only.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @GetMapping
    public List<ActivityResponse> getByTicket(@PathVariable UUID ticketId) {
        return activityService.getByTicket(ticketId);
    }
}
