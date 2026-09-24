import type { Label } from '../../api/labels'
import type { Ticket, TicketPriority, TicketStatus } from '../../api/tickets'
import { PRIORITY_LABELS, STATUS_LABELS } from './ticketDisplay'

/**
 * The ticket list's filters live in the address (?q=&status=&priority=
 * &assignee=&label=), so refresh, Back/Forward and shared links keep them.
 * The project's full ticket list is already loaded (the backend does not
 * paginate it), so filtering it here is complete - no request is made.
 */
export const FILTER_PARAMS = ['q', 'status', 'priority', 'assignee', 'label'] as const
export type FilterParam = (typeof FILTER_PARAMS)[number]

/** "open" is every status but DONE; the others are the backend's values. */
export type StatusFilter = 'open' | TicketStatus
/** "me", "unassigned", or a member's id. */
export type AssigneeFilter = 'me' | 'unassigned' | string

export interface TicketFilters {
  q: string
  status: StatusFilter | null
  priority: TicketPriority | null
  assignee: AssigneeFilter | null
  label: string | null
}

const STATUSES = new Set<string>(Object.keys(STATUS_LABELS))
const PRIORITIES = new Set<string>(Object.keys(PRIORITY_LABELS))

/**
 * The filters in the address, validated. A value that means nothing here -
 * an unknown status, a member or label id that is not in this project's
 * data - is ignored (as if absent) rather than breaking the page or
 * silently emptying the list.
 */
export function readTicketFilters(
  params: URLSearchParams,
  known: { memberIds: Set<string>; labelIds: Set<string> },
): TicketFilters {
  const status = params.get('status')
  const priority = params.get('priority')
  const assignee = params.get('assignee')
  const label = params.get('label')
  return {
    q: params.get('q') ?? '',
    status: status === 'open' || (status && STATUSES.has(status)) ? (status as StatusFilter) : null,
    priority: priority && PRIORITIES.has(priority) ? (priority as TicketPriority) : null,
    assignee:
      assignee === 'me' || assignee === 'unassigned' || (assignee && known.memberIds.has(assignee)) ? assignee : null,
    label: label && known.labelIds.has(label) ? label : null,
  }
}

export function hasActiveFilters(filters: TicketFilters): boolean {
  return (
    filters.q.trim() !== '' ||
    filters.status !== null ||
    filters.priority !== null ||
    filters.assignee !== null ||
    filters.label !== null
  )
}

/** The tickets that match, in the same order; the list itself is not changed. */
export function filterTickets(tickets: Ticket[], filters: TicketFilters, currentUserId: string): Ticket[] {
  const query = filters.q.trim().toLowerCase()
  return tickets.filter((ticket) => {
    if (query && !ticket.title.toLowerCase().includes(query) && !ticket.displayKey.toLowerCase().includes(query)) {
      return false
    }
    if (filters.status === 'open' ? ticket.status === 'DONE' : filters.status && ticket.status !== filters.status) {
      return false
    }
    if (filters.priority && ticket.priority !== filters.priority) {
      return false
    }
    if (filters.assignee === 'me' && ticket.assigneeId !== currentUserId) {
      return false
    }
    if (filters.assignee === 'unassigned' && ticket.assigneeId !== null) {
      return false
    }
    if (
      filters.assignee &&
      filters.assignee !== 'me' &&
      filters.assignee !== 'unassigned' &&
      ticket.assigneeId !== filters.assignee
    ) {
      return false
    }
    if (filters.label && !ticket.labels.some((label) => label.id === filters.label)) {
      return false
    }
    return true
  })
}

/** The labels on at least one of the tickets, once each, ordered by name - the label filter's options. */
export function labelsInTickets(tickets: Ticket[]): Label[] {
  const byId = new Map<string, Label>()
  for (const ticket of tickets) {
    for (const label of ticket.labels) {
      byId.set(label.id, label)
    }
  }
  return [...byId.values()].sort(
    (a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()) || a.name.localeCompare(b.name),
  )
}

/** A copy of `params` with one filter set (or removed, for null or ""). */
export function withFilter(params: URLSearchParams, name: FilterParam, value: string | null): URLSearchParams {
  const next = new URLSearchParams(params)
  if (value === null || value === '') {
    next.delete(name)
  } else {
    next.set(name, value)
  }
  return next
}

/** A copy of `params` without any ticket filter; other parameters are kept. */
export function withoutFilters(params: URLSearchParams): URLSearchParams {
  const next = new URLSearchParams(params)
  for (const name of FILTER_PARAMS) {
    next.delete(name)
  }
  return next
}
