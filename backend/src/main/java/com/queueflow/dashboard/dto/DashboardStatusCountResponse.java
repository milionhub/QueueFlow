package com.queueflow.dashboard.dto;

import com.queueflow.ticket.TicketStatus;

/** How many of the workspace's tickets currently have one status. */
public record DashboardStatusCountResponse(TicketStatus status, long count) {
}
