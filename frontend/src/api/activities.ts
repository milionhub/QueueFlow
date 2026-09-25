import type { AuthorizedRequest } from './client'

/** The backend's ActivityType. */
export type ActivityType =
  | 'TICKET_CREATED'
  | 'STATUS_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'ASSIGNEE_CHANGED'
  | 'TITLE_CHANGED'
  | 'DESCRIPTION_CHANGED'
  | 'LABEL_ADDED'
  | 'LABEL_REMOVED'

/**
 * The backend's ActivityResponse: one recorded change of a ticket, never
 * edited or deleted. What oldValue and newValue hold depends on `type`
 * (a status, a priority, a user id, a label name, a text, or null).
 */
export interface Activity {
  id: string
  type: ActivityType
  oldValue: string | null
  newValue: string | null
  ticketId: string
  /** Who made the change. */
  userId: string
  userName: string
  createdAt: string
}

/**
 * The ticket's history, oldest first (ties broken by id) - keep that
 * order. Read-only: the backend records it; there is nothing to write.
 */
export function listTicketActivities(
  request: AuthorizedRequest,
  ticketId: string,
  signal?: AbortSignal,
): Promise<Activity[]> {
  return request<Activity[]>(`/api/tickets/${encodeURIComponent(ticketId)}/activities`, { signal })
}
