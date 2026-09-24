import type { AuthorizedRequest } from './client'
import type { Label } from './labels'

/** The backend's TicketStatus, in workflow order. */
export type TicketStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE'

/** The backend's TicketPriority. */
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

/**
 * The backend's TicketResponse. The project, creator, number and display
 * key never change after creation.
 */
export interface Ticket {
  id: string
  /** Per project, from 1; with the project key it forms the display key. */
  ticketNumber: number
  /** e.g. "CORE-7" */
  displayKey: string
  title: string
  description: string | null
  status: TicketStatus
  priority: TicketPriority
  projectId: string
  projectKey: string
  creatorId: string
  /** null when the ticket is unassigned. */
  assigneeId: string | null
  createdAt: string
  updatedAt: string
  /** Ordered by name; empty when there are none. */
  labels: Label[]
}

/**
 * POST /api/tickets. The creator is always the caller; the number and
 * display key are allocated by the backend. Labels cannot be set here.
 */
export interface CreateTicketRequest {
  projectId: string
  /** Not trimmed by the backend: send it trimmed. */
  title: string
  description: string | null
  status: TicketStatus
  priority: TicketPriority
  assigneeId: string | null
}

/**
 * PATCH /api/tickets/{id}: an omitted field stays as it is. title, status
 * and priority cannot be cleared; `description: null` clears the
 * description and `assigneeId: null` unassigns. There are no project,
 * creator, number or display key fields: those never change.
 */
export interface UpdateTicketRequest {
  title?: string
  description?: string | null
  status?: TicketStatus
  priority?: TicketPriority
  assigneeId?: string | null
}

/** Every ticket of the project, ordered by ticket number - keep that order. Not paginated. */
export function listProjectTickets(
  request: AuthorizedRequest,
  projectId: string,
  signal?: AbortSignal,
): Promise<Ticket[]> {
  return request<Ticket[]>(`/api/projects/${encodeURIComponent(projectId)}/tickets`, { signal })
}

/** One ticket by its per-project number (7 in CORE-7); 404 if the project or number is unknown. */
export function getTicketByNumber(
  request: AuthorizedRequest,
  projectId: string,
  ticketNumber: number,
  signal?: AbortSignal,
): Promise<Ticket> {
  return request<Ticket>(`/api/projects/${encodeURIComponent(projectId)}/tickets/${ticketNumber}`, { signal })
}

/** Any member may create; a project or assignee outside the caller's workspace is 404. */
export function createTicket(request: AuthorizedRequest, body: CreateTicketRequest): Promise<Ticket> {
  return request<Ticket>('/api/tickets', { method: 'POST', body })
}

export function updateTicket(request: AuthorizedRequest, ticketId: string, body: UpdateTicketRequest): Promise<Ticket> {
  return request<Ticket>(`/api/tickets/${encodeURIComponent(ticketId)}`, { method: 'PATCH', body })
}

/** Idempotent: attaching an attached label changes nothing. Returns the updated ticket. */
export function addTicketLabel(request: AuthorizedRequest, ticketId: string, labelId: string): Promise<Ticket> {
  return request<Ticket>(`/api/tickets/${encodeURIComponent(ticketId)}/labels/${encodeURIComponent(labelId)}`, {
    method: 'PUT',
  })
}

/** Idempotent: removing a label that is not attached changes nothing. Returns the updated ticket. */
export function removeTicketLabel(request: AuthorizedRequest, ticketId: string, labelId: string): Promise<Ticket> {
  return request<Ticket>(`/api/tickets/${encodeURIComponent(ticketId)}/labels/${encodeURIComponent(labelId)}`, {
    method: 'DELETE',
  })
}
