import type { TicketPriority, TicketStatus } from '../../api/tickets'
import {
  PRIORITY_LABELS,
  PRIORITY_TEXT_CLASSES,
  STATUS_BADGE_CLASSES,
  STATUS_LABELS,
  STATUS_TONE,
} from './ticketDisplay'

/**
 * A status's icon: an outline that fills as the work advances - dashed
 * (Backlog), empty (To do), half (In progress), three quarters (Review),
 * a check (Done). Decorative: the name is always next to it.
 */
export function StatusIcon({ status, className = 'size-3.5' }: { status: TicketStatus; className?: string }) {
  const tone = STATUS_TONE[status].text
  return (
    <svg viewBox="0 0 14 14" fill="none" aria-hidden="true" className={`shrink-0 ${tone} ${className}`}>
      {status === 'DONE' ? (
        <>
          <circle cx="7" cy="7" r="6" fill="currentColor" />
          <path
            d="m4.4 7.1 1.8 1.8 3.4-3.6"
            stroke="#fff"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </>
      ) : (
        <>
          <circle
            cx="7"
            cy="7"
            r="5.75"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeDasharray={status === 'BACKLOG' ? '2.2 2' : undefined}
          />
          {status === 'IN_PROGRESS' && <path d="M7 3.5a3.5 3.5 0 0 1 0 7Z" fill="currentColor" />}
          {status === 'REVIEW' && <path d="M7 3.5A3.5 3.5 0 1 1 3.5 7H7Z" fill="currentColor" />}
        </>
      )}
    </svg>
  )
}

/** A small pill with the status's icon and name; the tint only reinforces the text. */
export function StatusBadge({ status }: { status: TicketStatus }) {
  return (
    <span
      className={`inline-flex h-5 items-center gap-1 rounded-full pr-2 pl-1.5 text-xs font-medium whitespace-nowrap ${STATUS_BADGE_CLASSES[status]}`}
    >
      <StatusIcon status={status} className="size-3" />
      {STATUS_LABELS[status]}
    </span>
  )
}

const FILLED_BARS: Record<Exclude<TicketPriority, 'CRITICAL'>, number> = { LOW: 1, MEDIUM: 2, HIGH: 3 }

/**
 * A priority's icon: one to three bars (Low, Medium, High), or a red alert
 * square for Critical. Decorative: the name is always next to it, or in
 * accessible text.
 */
export function PriorityIcon({
  priority,
  className = 'size-3.5',
  inverse = false,
}: {
  priority: TicketPriority
  className?: string
  /** On a dark surface: light bars. */
  inverse?: boolean
}) {
  if (priority === 'CRITICAL') {
    return (
      <svg viewBox="0 0 14 14" aria-hidden="true" className={`shrink-0 text-danger ${className}`}>
        <rect x="1" y="1" width="12" height="12" rx="3" fill="currentColor" />
        <path d="M7 3.8v3.9" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" />
        <circle cx="7" cy="10" r=".95" fill="#fff" />
      </svg>
    )
  }
  const filled = FILLED_BARS[priority]
  const tone = inverse ? 'text-[#c7cdf5]' : priority === 'HIGH' ? 'text-ink' : 'text-ink-muted'
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true" className={`shrink-0 ${tone} ${className}`}>
      {[0, 1, 2].map((bar) => (
        <rect
          key={bar}
          x={1.5 + bar * 4}
          y={9 - bar * 3}
          width="3"
          height={4 + bar * 3}
          rx="1"
          fill="currentColor"
          opacity={bar < filled ? 1 : 0.22}
        />
      ))}
    </svg>
  )
}

/** The priority's icon and name, never colour alone. */
export function PriorityLabel({ priority }: { priority: TicketPriority }) {
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs whitespace-nowrap ${PRIORITY_TEXT_CLASSES[priority]}`}>
      <PriorityIcon priority={priority} />
      <span>
        <span className="sr-only">Priority: </span>
        {PRIORITY_LABELS[priority]}
      </span>
    </span>
  )
}
