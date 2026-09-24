package com.queueflow.dashboard;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.queueflow.dashboard.dto.DashboardProjectResponse;
import com.queueflow.dashboard.dto.DashboardStatusCountResponse;
import com.queueflow.ticket.Ticket;

/**
 * The dashboard's read-only queries: aggregates and short projected lists,
 * each a single SQL statement whatever the number of projects or tickets.
 * A plain Repository (not JpaRepository), so it offers no save or delete.
 *
 * Every query is constrained to the given workspace through the ticket's
 * project - including the ones that also filter by assignee, so a ticket
 * of another workspace can never be counted or listed even if its assignee
 * id were known. "Open" means any status but DONE.
 *
 * Tickets reach their workspace through projects.workspace_id, so every
 * ticket query joins projects; t.assignee.id reads tickets.assignee_id
 * directly, and only the two lists join users (for the assignee's name).
 * The lists' Limit becomes the SQL row limit (FETCH FIRST n ROWS ONLY).
 */
public interface DashboardRepository extends Repository<Ticket, UUID> {

    /**
     * Every project of the workspace - including those without tickets
     * (LEFT JOIN: COUNT of no ticket rows is 0) - in the order of
     * ProjectRepository.findAllInWorkspaceSortedByName.
     */
    @Query("""
            SELECT new com.queueflow.dashboard.dto.DashboardProjectResponse(p.id, p.key, p.name,
                COUNT(CASE WHEN t.status <> com.queueflow.ticket.TicketStatus.DONE THEN 1 END), COUNT(t))
            FROM Project p LEFT JOIN Ticket t ON t.project = p
            WHERE p.workspace.id = :workspaceId
            GROUP BY p.id, p.key, p.name
            ORDER BY LOWER(p.name) ASC, p.name ASC, p.id ASC""")
    List<DashboardProjectResponse> findProjectSummaries(@Param("workspaceId") UUID workspaceId);

    /** Only statuses that occur; the service fills in the others with 0. */
    @Query("""
            SELECT new com.queueflow.dashboard.dto.DashboardStatusCountResponse(t.status, COUNT(t))
            FROM Ticket t
            WHERE t.project.workspace.id = :workspaceId
            GROUP BY t.status""")
    List<DashboardStatusCountResponse> countByStatus(@Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT COUNT(t) FROM Ticket t
            WHERE t.project.workspace.id = :workspaceId
                AND t.assignee IS NULL
                AND t.status <> com.queueflow.ticket.TicketStatus.DONE""")
    long countUnassignedOpen(@Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT COUNT(t) FROM Ticket t
            WHERE t.project.workspace.id = :workspaceId
                AND t.assignee.id = :assigneeId
                AND t.status <> com.queueflow.ticket.TicketStatus.DONE""")
    long countOpenAssignedTo(@Param("workspaceId") UUID workspaceId, @Param("assigneeId") UUID assigneeId);

    /**
     * The assignee's open tickets, most urgent first: by priority (the enum
     * is stored as text, so its rank is spelled out), then most recently
     * updated, then id - unique, so the order and the cut at the limit are
     * deterministic.
     */
    @Query("""
            SELECT new com.queueflow.dashboard.DashboardTicketRow(t.id, p.id, p.key, t.ticketNumber, t.title,
                t.status, t.priority, a.id, a.name, t.updatedAt)
            FROM Ticket t JOIN t.project p JOIN t.assignee a
            WHERE p.workspace.id = :workspaceId
                AND a.id = :assigneeId
                AND t.status <> com.queueflow.ticket.TicketStatus.DONE
            ORDER BY
                CASE t.priority
                    WHEN com.queueflow.ticket.TicketPriority.CRITICAL THEN 0
                    WHEN com.queueflow.ticket.TicketPriority.HIGH THEN 1
                    WHEN com.queueflow.ticket.TicketPriority.MEDIUM THEN 2
                    ELSE 3
                END ASC,
                t.updatedAt DESC,
                t.id DESC""")
    List<DashboardTicketRow> findOpenAssignedTo(@Param("workspaceId") UUID workspaceId,
            @Param("assigneeId") UUID assigneeId, Limit limit);

    /** Any status, DONE included; assignee columns are null for an unassigned ticket (LEFT JOIN). */
    @Query("""
            SELECT new com.queueflow.dashboard.DashboardTicketRow(t.id, p.id, p.key, t.ticketNumber, t.title,
                t.status, t.priority, a.id, a.name, t.updatedAt)
            FROM Ticket t JOIN t.project p LEFT JOIN t.assignee a
            WHERE p.workspace.id = :workspaceId
            ORDER BY t.updatedAt DESC, t.id DESC""")
    List<DashboardTicketRow> findRecentlyUpdated(@Param("workspaceId") UUID workspaceId, Limit limit);
}
