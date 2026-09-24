package com.queueflow.dashboard;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueflow.dashboard.dto.DashboardAssignedResponse;
import com.queueflow.dashboard.dto.DashboardResponse;
import com.queueflow.dashboard.dto.DashboardStatusCountResponse;
import com.queueflow.dashboard.dto.DashboardTicketResponse;
import com.queueflow.security.AuthenticatedUser;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.workspace.WorkspaceAccess;

@Service
public class DashboardService {

    static final int ASSIGNED_TICKETS_LIMIT = 10;
    static final int RECENTLY_UPDATED_LIMIT = 8;

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    /**
     * The same dashboard for ADMIN and MEMBER; only assignedToMe depends on
     * who asks. Any workspace other than the caller's is not found, decided
     * before any query runs. Six statements in total, whatever the size of
     * the workspace (see DashboardRepository), in one read-only
     * transaction. Under PostgreSQL's READ COMMITTED each statement reads
     * its own snapshot, so a ticket changed mid-request may be counted
     * slightly differently by two sections - acceptable for an overview
     * that is reloaded, not a report.
     */
    @Transactional(readOnly = true)
    public DashboardResponse getByWorkspace(AuthenticatedUser actor, UUID workspaceId) {
        WorkspaceAccess.requireOwnWorkspace(actor, workspaceId);
        UUID ownWorkspaceId = actor.workspaceId();

        DashboardAssignedResponse assignedToMe = new DashboardAssignedResponse(
                dashboardRepository.countOpenAssignedTo(ownWorkspaceId, actor.userId()),
                tickets(dashboardRepository.findOpenAssignedTo(ownWorkspaceId, actor.userId(),
                        Limit.of(ASSIGNED_TICKETS_LIMIT))));

        return new DashboardResponse(
                statusCounts(ownWorkspaceId),
                dashboardRepository.countUnassignedOpen(ownWorkspaceId),
                dashboardRepository.findProjectSummaries(ownWorkspaceId),
                assignedToMe,
                tickets(dashboardRepository.findRecentlyUpdated(ownWorkspaceId, Limit.of(RECENTLY_UPDATED_LIMIT))));
    }

    /** All five statuses in enum (workflow) order; a status no ticket has counts 0. */
    private List<DashboardStatusCountResponse> statusCounts(UUID workspaceId) {
        Map<TicketStatus, Long> counts = dashboardRepository.countByStatus(workspaceId).stream()
                .collect(Collectors.toMap(DashboardStatusCountResponse::status, DashboardStatusCountResponse::count));
        return Arrays.stream(TicketStatus.values())
                .map(status -> new DashboardStatusCountResponse(status, counts.getOrDefault(status, 0L)))
                .toList();
    }

    private static List<DashboardTicketResponse> tickets(List<DashboardTicketRow> rows) {
        return rows.stream().map(DashboardTicketResponse::from).toList();
    }
}
