import type { TicketPriority, TicketStatus } from '../../api/tickets'
import { i18n } from '../../i18n'
import { stableHash } from '../auth/userDisplay'

/**
 * How ticket statuses and priorities are shown. The API values stay as
 * they are - these are what is sent and compared - and only the functions
 * and tables here turn them into words (in the interface language) and
 * styles.
 */

/** Every status, in workflow order: the board's columns and every list of statuses. */
export const TICKET_STATUSES: readonly TicketStatus[] = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE']

/** Every priority, lowest first. */
export const TICKET_PRIORITIES: readonly TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

/**
 * A status's name in the interface language. For code outside components;
 * a component uses its own `t` (useTranslation), so it re-renders when the
 * language changes.
 */
export function statusLabel(status: TicketStatus): string {
  return i18n.t(`tickets:status.${status}`)
}

/** A priority's name in the interface language (see statusLabel). */
export function priorityLabel(priority: TicketPriority): string {
  return i18n.t(`tickets:priority.${priority}`)
}

/**
 * The status chip's tint and text, and the colour of its icon (and of the
 * dashboard's status bar). Every status also has its own icon shape, and
 * its name is always written next to it: colour is never the only cue.
 */
export const STATUS_BADGE_CLASSES: Record<TicketStatus, string> = {
  BACKLOG: 'bg-canvas-strong text-ink-muted',
  TODO: 'bg-status-todo-subtle text-status-todo-text',
  IN_PROGRESS: 'bg-accent-subtle text-accent',
  REVIEW: 'bg-status-review-subtle text-status-review',
  DONE: 'bg-success-subtle text-success-text',
}

/**
 * Each status's hue: `text` for its icon, `fill` for bars and dots, `soft`
 * for tinted discs and pills, `rule` for the thin accent line of a Board
 * column (an inset shadow, so it never changes the column's size).
 */
export const STATUS_TONE: Record<TicketStatus, { text: string; fill: string; soft: string; rule: string }> = {
  BACKLOG: {
    text: 'text-status-backlog',
    fill: 'bg-status-backlog',
    soft: 'bg-canvas-strong text-ink-muted',
    rule: 'shadow-[inset_0_3px_0_0_var(--color-status-backlog)]',
  },
  TODO: {
    text: 'text-status-todo',
    fill: 'bg-status-todo',
    soft: 'bg-status-todo-subtle text-status-todo-text',
    rule: 'shadow-[inset_0_3px_0_0_var(--color-status-todo)]',
  },
  IN_PROGRESS: {
    text: 'text-accent',
    fill: 'bg-accent',
    soft: 'bg-accent-subtle text-accent',
    rule: 'shadow-[inset_0_3px_0_0_var(--color-accent)]',
  },
  REVIEW: {
    text: 'text-status-review',
    fill: 'bg-status-review',
    soft: 'bg-status-review-subtle text-status-review',
    rule: 'shadow-[inset_0_3px_0_0_var(--color-status-review)]',
  },
  DONE: {
    text: 'text-success',
    fill: 'bg-success',
    soft: 'bg-success-subtle text-success-text',
    rule: 'shadow-[inset_0_3px_0_0_var(--color-success)]',
  },
}

/** The label next to the priority icon: only High and Critical draw attention. */
export const PRIORITY_TEXT_CLASSES: Record<TicketPriority, string> = {
  LOW: 'text-ink-muted',
  MEDIUM: 'text-ink-muted',
  HIGH: 'text-ink',
  CRITICAL: 'font-medium text-danger',
}

/**
 * The soft dot of a label chip, picked from the label's name. Labels have
 * no colour in the backend: this is only presentation, and the same name
 * always gets the same dot.
 */
const LABEL_DOTS = [
  'bg-[#6173e0]',
  'bg-[#2f9e62]',
  'bg-[#d08a1f]',
  'bg-[#9061e0]',
  'bg-[#2b93b3]',
  'bg-[#d9536f]',
  'bg-[#7c8798]',
  'bg-[#7a9a2c]',
] as const

export function labelDotClass(name: string): string {
  return LABEL_DOTS[stableHash(name) % LABEL_DOTS.length]
}
