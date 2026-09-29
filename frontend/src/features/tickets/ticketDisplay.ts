import type { TicketPriority, TicketStatus } from '../../api/tickets'
import { stableHash } from '../auth/userDisplay'

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

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
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
