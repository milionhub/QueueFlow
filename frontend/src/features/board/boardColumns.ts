import type { Ticket, TicketStatus } from '../../api/tickets'

/** The board's columns, left to right: the backend's statuses in workflow order. */
export const STATUS_ORDER: readonly TicketStatus[] = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE']

/**
 * The tickets of each column. Every ticket lands in exactly one column, and
 * within a column they are in ticket-number order - the backend's list
 * order. Tickets have no rank, so a moved ticket takes its number's place in
 * its new column; there is no order of its own to keep. `tickets` is not
 * changed.
 */
export function groupByStatus(tickets: readonly Ticket[]): Record<TicketStatus, Ticket[]> {
  const columns: Record<TicketStatus, Ticket[]> = { BACKLOG: [], TODO: [], IN_PROGRESS: [], REVIEW: [], DONE: [] }
  for (const ticket of tickets) {
    columns[ticket.status].push(ticket)
  }
  for (const status of STATUS_ORDER) {
    columns[status].sort((a, b) => a.ticketNumber - b.ticketNumber)
  }
  return columns
}
