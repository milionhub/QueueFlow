import type { TicketPriority, TicketStatus } from '../../api/tickets'

/**
 * How ticket statuses and priorities are shown. The API values stay as
 * they are; only these tables turn them into words and styles, so a later
 * translation replaces the label maps and nothing else.
 */

export const STATUS_LABELS: Record<TicketStatus, string> = {
  BACKLOG: 'Backlog',
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  REVIEW: 'Review',
  DONE: 'Done',
}

/** Three quiet tones: not started, in motion, finished. */
export const STATUS_BADGE_CLASSES: Record<TicketStatus, string> = {
  BACKLOG: 'border-line bg-canvas text-ink-muted',
  TODO: 'border-line bg-canvas text-ink-muted',
  IN_PROGRESS: 'border-accent/20 bg-accent-subtle text-accent',
  REVIEW: 'border-accent/20 bg-accent-subtle text-accent',
  DONE: 'border-success/20 bg-success/5 text-success',
}

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
}

/** The dot and the label next to it: only High and Critical draw attention. */
export const PRIORITY_CLASSES: Record<TicketPriority, { dot: string; label: string }> = {
  LOW: { dot: 'border border-ink-subtle', label: 'text-ink-subtle' },
  MEDIUM: { dot: 'bg-ink-subtle', label: 'text-ink-muted' },
  HIGH: { dot: 'bg-warning', label: 'text-ink' },
  CRITICAL: { dot: 'bg-danger', label: 'font-medium text-danger' },
}
