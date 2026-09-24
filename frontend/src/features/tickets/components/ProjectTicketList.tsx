import { Link } from 'react-router'

import type { Ticket } from '../../../api/tickets'
import { formatRelativeTime } from '../../../lib/relativeTime'
import { ticketPath } from '../../../routes/paths'
import { PriorityLabel, StatusBadge } from '../TicketBadges'
import { LabelChips } from './LabelChips'

interface ProjectTicketListProps {
  tickets: Ticket[]
  memberName: (userId: string) => string
  /** When the data arrived: every relative time in the list is measured from it. */
  now: number
}

/**
 * The project's tickets in the backend's order. Each row's title is its
 * link. Container queries adapt the row to the list's width: two lines
 * when narrow, one line from 36rem, labels from 56rem.
 */
export function ProjectTicketList({ tickets, memberName, now }: ProjectTicketListProps) {
  return (
    <div className="@container">
      <ul className="divide-y divide-line rounded-md border border-line bg-surface">
        {tickets.map((ticket) => (
          <TicketRow key={ticket.id} ticket={ticket} memberName={memberName} now={now} />
        ))}
      </ul>
    </div>
  )
}

function TicketRow({ ticket, memberName, now }: { ticket: Ticket } & Omit<ProjectTicketListProps, 'tickets'>) {
  const time = formatRelativeTime(ticket.updatedAt, now)
  const assignee = ticket.assigneeId ? memberName(ticket.assigneeId) : null
  return (
    <li className="flex flex-col gap-1 px-4 py-2.5 @xl:flex-row @xl:items-center @xl:gap-4">
      <div className="flex min-w-0 flex-1 items-baseline gap-3">
        <span className="min-w-16 shrink-0 font-mono text-xs whitespace-nowrap text-ink-subtle">{ticket.displayKey}</span>
        <Link
          to={ticketPath(ticket.projectKey, ticket.ticketNumber)}
          title={ticket.title}
          className="min-w-0 truncate text-sm text-ink underline-offset-4 hover:text-accent hover:underline"
        >
          {ticket.title}
        </Link>
      </div>
      {ticket.labels.length > 0 && (
        <div className="hidden max-w-60 shrink-0 @4xl:block">
          <LabelChips labels={ticket.labels} limit={2} />
        </div>
      )}
      <div className="flex shrink-0 items-center gap-3 text-xs text-ink-muted">
        <span className="@xl:w-18">
          <PriorityLabel priority={ticket.priority} />
        </span>
        <span className="@xl:w-24">
          <StatusBadge status={ticket.status} />
        </span>
        <span className={`max-w-32 truncate @xl:w-28 ${assignee ? '' : 'text-ink-subtle'}`} title={assignee ?? undefined}>
          <span className="sr-only">Assignee: </span>
          {assignee ?? 'Unassigned'}
        </span>
        <time dateTime={ticket.updatedAt} title={time.full} className="ml-auto tabular-nums @xl:ml-0 @xl:w-12 @xl:text-right">
          <span aria-hidden="true">{time.short}</span>
          <span className="sr-only">Updated {time.spoken}</span>
        </time>
      </div>
    </li>
  )
}
