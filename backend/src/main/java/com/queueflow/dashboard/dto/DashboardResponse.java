package com.queueflow.dashboard.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the dashboard shows, for the caller's workspace. Totals are
 * deliberately not repeated: all tickets = the sum of statusCounts, open
 * tickets = that sum minus DONE. The workspace and the current user are
 * already known to the client and are not included.
 */
public record DashboardResponse(
        @Schema(description = "One entry per ticket status, always all five, in workflow order "
                + "(BACKLOG, TODO, IN_PROGRESS, REVIEW, DONE); 0 when no ticket has that status")
        List<DashboardStatusCountResponse> statusCounts,
        @Schema(description = "Open (not DONE) tickets without an assignee")
        long unassignedOpenCount,
        @Schema(description = "Every project of the workspace, including those without tickets, ordered "
                + "case-insensitively by name")
        List<DashboardProjectResponse> projects,
        DashboardAssignedResponse assignedToMe,
        @Schema(description = "At most 8 tickets, DONE included, most recently updated first")
        List<DashboardTicketResponse> recentlyUpdated) {

    public DashboardResponse {
        statusCounts = List.copyOf(statusCounts);
        projects = List.copyOf(projects);
        recentlyUpdated = List.copyOf(recentlyUpdated);
    }
}
