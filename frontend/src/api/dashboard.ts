import type { AuthorizedRequest } from './client'
import type { TicketPriority, TicketStatus } from './tickets'

/** DashboardStatusCountResponse */
export interface DashboardStatusCount {
  status: TicketStatus
  count: number
}

/** DashboardProjectResponse: `openTicketCount` excludes DONE, `ticketCount` includes it. */
export interface DashboardProject {
  id: string
  key: string
  name: string
  openTicketCount: number
  ticketCount: number
}

/** DashboardTicketResponse */
export interface DashboardTicket {
  id: string
  projectId: string
  /** e.g. "CORE-7" */
  displayKey: string
  title: string
  status: TicketStatus
  priority: TicketPriority
  /** null when the ticket is unassigned (and then so is assigneeName). */
  assigneeId: string | null
  assigneeName: string | null
  updatedAt: string
}

/** DashboardAssignedResponse: `openCount` is the true total; `tickets` holds at most 10. */
export interface DashboardAssigned {
  openCount: number
  tickets: DashboardTicket[]
}

/**
 * The backend's DashboardResponse. It carries no totals on purpose: all
 * tickets = the sum of statusCounts, open tickets = that sum minus DONE.
 */
export interface DashboardResponse {
  /** Always all five statuses, in workflow order. */
  statusCounts: DashboardStatusCount[]
  unassignedOpenCount: number
  projects: DashboardProject[]
  assignedToMe: DashboardAssigned
  /** At most 8, DONE included, most recently updated first. */
  recentlyUpdated: DashboardTicket[]
}

/** GET /api/workspaces/{id}/dashboard: only the caller's own workspace; any other id is 404. */
export function getDashboard(
  request: AuthorizedRequest,
  workspaceId: string,
  signal?: AbortSignal,
): Promise<DashboardResponse> {
  return request<DashboardResponse>(`/api/workspaces/${encodeURIComponent(workspaceId)}/dashboard`, { signal })
}
