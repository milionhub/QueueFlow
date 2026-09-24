package com.queueflow.dashboard.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One project of the workspace with its ticket counts. Built directly by
 * the aggregate query in DashboardRepository - no tickets are loaded.
 */
public record DashboardProjectResponse(
        UUID id,
        String key,
        String name,
        @Schema(description = "Tickets of this project whose status is not DONE")
        long openTicketCount,
        @Schema(description = "All tickets of this project, DONE included")
        long ticketCount) {
}
