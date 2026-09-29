import type { Activity } from '../../api/activities'
import type { TicketPriority, TicketStatus } from '../../api/tickets'
import { i18n } from '../../i18n'
import type { activity as activityMessages } from '../../i18n/locales/en/activity'
import { priorityLabel, statusLabel, TICKET_PRIORITIES, TICKET_STATUSES } from './ticketDisplay'

/** Which icon an entry gets; the words alone carry the meaning. */
export type ActivityKind = 'created' | 'title' | 'description' | 'status' | 'priority' | 'assignee' | 'label' | 'other'

/**
 * A piece of an entry's sentence: plain text, or a value to emphasize.
 * `full` is set when the value was shortened for display; `actor` marks
 * the person who did it (wherever the language puts them in the sentence).
 */
export type ActivitySegment = string | { value: string; full?: string; actor?: true }

export interface ActivityText {
  kind: ActivityKind
  segments: ActivitySegment[]
}

type EntryKey = keyof typeof activityMessages.entries

const TITLE_DISPLAY_LENGTH = 80

/** Marks where a value goes in a translated sentence; it never appears in a message. */
const MARK = '\u0000'

function shorten(text: string, max: number): ActivitySegment {
  return text.length <= max ? { value: text } : { value: `${text.slice(0, max - 1).trimEnd()}…`, full: text }
}

/**
 * One whole sentence in the interface language, cut at its placeholders:
 * the translation decides the order of the words and where the actor and
 * the values go; the values themselves (names, labels, titles) are the
 * backend's data and are never translated.
 */
function sentence(key: EntryKey, values: Record<string, ActivitySegment>): ActivitySegment[] {
  const placeholders = Object.fromEntries(Object.keys(values).map((name) => [name, `${MARK}${name}${MARK}`]))
  // The placeholders are built from `values`, which the typed `t` cannot follow; the key itself is still
  // checked (EntryKey), and every sentence's placeholders are the ones passed here.
  const translate = i18n.t as unknown as (key: string, options: Record<string, string>) => string
  const text = translate(`activity:entries.${key}`, placeholders)
  return text
    .split(MARK)
    .map((part, index) => (index % 2 === 1 ? (values[part] ?? '') : part))
    .filter((part) => part !== '')
}

function statusValue(value: string | null): ActivitySegment {
  if (value === null) {
    return { value: i18n.t('activity:none') }
  }
  return { value: (TICKET_STATUSES as readonly string[]).includes(value) ? statusLabel(value as TicketStatus) : value }
}

function priorityValue(value: string | null): ActivitySegment {
  if (value === null) {
    return { value: i18n.t('activity:none') }
  }
  return {
    value: (TICKET_PRIORITIES as readonly string[]).includes(value) ? priorityLabel(value as TicketPriority) : value,
  }
}

/**
 * One history entry as a sentence in the interface language ("Ana moved
 * the ticket from To do to In progress" / "Ana movió el ticket de Por hacer
 * a En progreso"), from the backend's values only - nothing is inferred,
 * and the stored values are never changed: statuses and priorities are
 * named in the interface language, people and labels as they are.
 * `memberName` resolves assignee ids (a removed member is "Former member");
 * values it does not know, or a type added later, fall back to neutral
 * wording instead of failing.
 */
export function describeActivity(activity: Activity, memberName: (userId: string) => string): ActivityText {
  const actor: ActivitySegment = { value: activity.userName || i18n.t('common:people.unknownUser'), actor: true }
  const { oldValue, newValue } = activity
  // The actor's own name comes with the entry, even if they left the members loaded.
  const person = (userId: string): ActivitySegment => ({
    value: userId === activity.userId ? activity.userName : memberName(userId),
  })

  switch (activity.type) {
    case 'TICKET_CREATED':
      return { kind: 'created', segments: sentence('created', { actor }) }
    case 'TITLE_CHANGED':
      return {
        kind: 'title',
        segments: newValue
          ? sentence('renamedTo', { actor, title: shorten(newValue, TITLE_DISPLAY_LENGTH) })
          : sentence('renamed', { actor }),
      }
    case 'DESCRIPTION_CHANGED':
      if (oldValue === null && newValue !== null) {
        return { kind: 'description', segments: sentence('descriptionAdded', { actor }) }
      }
      if (oldValue !== null && newValue === null) {
        return { kind: 'description', segments: sentence('descriptionRemoved', { actor }) }
      }
      return { kind: 'description', segments: sentence('descriptionUpdated', { actor }) }
    case 'STATUS_CHANGED':
      return {
        kind: 'status',
        segments: sentence('statusChanged', { actor, from: statusValue(oldValue), to: statusValue(newValue) }),
      }
    case 'PRIORITY_CHANGED':
      return {
        kind: 'priority',
        segments: sentence('priorityChanged', { actor, from: priorityValue(oldValue), to: priorityValue(newValue) }),
      }
    case 'ASSIGNEE_CHANGED':
      if (oldValue === null && newValue !== null) {
        return {
          kind: 'assignee',
          segments:
            newValue === activity.userId
              ? sentence('assignedSelf', { actor })
              : sentence('assigned', { actor, person: person(newValue) }),
        }
      }
      if (oldValue !== null && newValue === null) {
        return {
          kind: 'assignee',
          segments:
            oldValue === activity.userId
              ? sentence('unassignedSelf', { actor })
              : sentence('unassigned', { actor, person: person(oldValue) }),
        }
      }
      if (oldValue !== null && newValue !== null) {
        return {
          kind: 'assignee',
          segments: sentence('reassigned', { actor, from: person(oldValue), to: person(newValue) }),
        }
      }
      return { kind: 'assignee', segments: sentence('assigneeChanged', { actor }) }
    case 'LABEL_ADDED':
      return {
        kind: 'label',
        segments: newValue
          ? sentence('labelAdded', { actor, label: { value: newValue } })
          : sentence('labelAddedUnknown', { actor }),
      }
    case 'LABEL_REMOVED':
      return {
        kind: 'label',
        segments: oldValue
          ? sentence('labelRemoved', { actor, label: { value: oldValue } })
          : sentence('labelRemovedUnknown', { actor }),
      }
    default:
      return { kind: 'other', segments: sentence('other', { actor }) }
  }
}
