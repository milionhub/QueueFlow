package com.queueflow.dashboard.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** The acting user's open tickets: the true total, and the most urgent ones. */
public record DashboardAssignedResponse(
        @Schema(description = "All open (not DONE) tickets assigned to the caller; may exceed the listed tickets")
        long openCount,
        @Schema(description = "At most 10: CRITICAL first, then HIGH, MEDIUM, LOW; most recently updated first "
                + "within a priority")
        List<DashboardTicketResponse> tickets) {

    public DashboardAssignedResponse {
        tickets = List.copyOf(tickets);
    }
}
