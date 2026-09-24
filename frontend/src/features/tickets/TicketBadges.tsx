import type { TicketPriority, TicketStatus } from '../../api/tickets'
import { PRIORITY_CLASSES, PRIORITY_LABELS, STATUS_BADGE_CLASSES, STATUS_LABELS } from './ticketDisplay'

/** A small chip with the status's name; the tone only reinforces the text. */
export function StatusBadge({ status }: { status: TicketStatus }) {
  return (
    <span
      className={`inline-flex h-5 items-center rounded border px-1.5 text-xs font-medium whitespace-nowrap ${STATUS_BADGE_CLASSES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}

/** A dot and the priority's name, never colour alone. */
export function PriorityLabel({ priority }: { priority: TicketPriority }) {
  const classes = PRIORITY_CLASSES[priority]
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs whitespace-nowrap ${classes.label}`}>
      <span aria-hidden="true" className={`size-2 shrink-0 rounded-full ${classes.dot}`} />
      <span>
        <span className="sr-only">Priority: </span>
        {PRIORITY_LABELS[priority]}
      </span>
    </span>
  )
}
