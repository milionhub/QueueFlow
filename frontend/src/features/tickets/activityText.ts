import type { Activity } from '../../api/activities'
import type { TicketPriority, TicketStatus } from '../../api/tickets'
import { PRIORITY_LABELS, STATUS_LABELS } from './ticketDisplay'

/** Which icon an entry gets; the words alone carry the meaning. */
export type ActivityKind = 'created' | 'title' | 'description' | 'status' | 'priority' | 'assignee' | 'label' | 'other'

/**
 * A piece of an entry's sentence: plain text, or a value to emphasize.
 * `full` is set when the value was shortened for display.
 */
export type ActivitySegment = string | { value: string; full?: string }

export interface ActivityText {
  kind: ActivityKind
  /** Starts with the actor. */
  segments: ActivitySegment[]
}

const UNKNOWN_USER = 'Unknown user'
const TITLE_DISPLAY_LENGTH = 80

function shorten(text: string, max: number): ActivitySegment {
  return text.length <= max ? { value: text } : { value: `${text.slice(0, max - 1).trimEnd()}…`, full: text }
}

function statusLabel(value: string | null): string {
  return value === null ? 'none' : (STATUS_LABELS[value as TicketStatus] ?? value)
}

function priorityLabel(value: string | null): string {
  return value === null ? 'none' : (PRIORITY_LABELS[value as TicketPriority] ?? value)
}

/**
 * One history entry as an English sentence ("Ana moved the ticket from To do
 * to In progress"), from the backend's values only - nothing is inferred.
 * `memberName` resolves assignee ids; values it does not know, or a type
 * added later, fall back to neutral wording instead of failing.
 */
export function describeActivity(activity: Activity, memberName: (userId: string) => string): ActivityText {
  const actor: ActivitySegment = { value: activity.userName || UNKNOWN_USER }
  const { oldValue, newValue } = activity
  // The actor's own name comes with the entry, even if they left the members loaded.
  const person = (userId: string): string => (userId === activity.userId ? activity.userName : memberName(userId))

  switch (activity.type) {
    case 'TICKET_CREATED':
      return { kind: 'created', segments: [actor, ' created the ticket'] }
    case 'TITLE_CHANGED':
      return {
        kind: 'title',
        segments: newValue
          ? [actor, ' renamed the ticket to “', shorten(newValue, TITLE_DISPLAY_LENGTH), '”']
          : [actor, ' renamed the ticket'],
      }
    case 'DESCRIPTION_CHANGED':
      if (oldValue === null && newValue !== null) {
        return { kind: 'description', segments: [actor, ' added a description'] }
      }
      if (oldValue !== null && newValue === null) {
        return { kind: 'description', segments: [actor, ' removed the description'] }
      }
      return { kind: 'description', segments: [actor, ' updated the description'] }
    case 'STATUS_CHANGED':
      return {
        kind: 'status',
        segments: [
          actor,
          ' moved the ticket from ',
          { value: statusLabel(oldValue) },
          ' to ',
          { value: statusLabel(newValue) },
        ],
      }
    case 'PRIORITY_CHANGED':
      return {
        kind: 'priority',
        segments: [
          actor,
          ' changed priority from ',
          { value: priorityLabel(oldValue) },
          ' to ',
          { value: priorityLabel(newValue) },
        ],
      }
    case 'ASSIGNEE_CHANGED':
      if (oldValue === null && newValue !== null) {
        return {
          kind: 'assignee',
          segments:
            newValue === activity.userId
              ? [actor, ' assigned the ticket to themselves']
              : [actor, ' assigned the ticket to ', { value: person(newValue) }],
        }
      }
      if (oldValue !== null && newValue === null) {
        return {
          kind: 'assignee',
          segments:
            oldValue === activity.userId
              ? [actor, ' unassigned themselves']
              : [actor, ' unassigned ', { value: person(oldValue) }],
        }
      }
      if (oldValue !== null && newValue !== null) {
        return {
          kind: 'assignee',
          segments: [
            actor,
            ' reassigned the ticket from ',
            { value: person(oldValue) },
            ' to ',
            { value: person(newValue) },
          ],
        }
      }
      return { kind: 'assignee', segments: [actor, ' changed the assignee'] }
    case 'LABEL_ADDED':
      return {
        kind: 'label',
        segments: newValue ? [actor, ' added label ', { value: newValue }] : [actor, ' added a label'],
      }
    case 'LABEL_REMOVED':
      return {
        kind: 'label',
        segments: oldValue ? [actor, ' removed label ', { value: oldValue }] : [actor, ' removed a label'],
      }
    default:
      return { kind: 'other', segments: [actor, ' updated the ticket'] }
  }
}
