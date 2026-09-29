import { Link } from 'react-router'

import type { Ticket } from '../../../api/tickets'
import { Avatar, EmptyAvatar } from '../../../components/ui/Avatar'
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
 * The project's tickets in the backend's order. The whole row is the link
 * to the ticket (its title is the link's text). Container queries adapt the
 * row to the list's width: two lines when narrow, one line from 36rem,
 * labels from 56rem.
 */
export function ProjectTicketList({ tickets, memberName, now }: ProjectTicketListProps) {
  return (
    <div className="@container">
      <ul className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface shadow-xs">
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
    <li className="group relative flex flex-col gap-2 px-4 py-3 transition-colors duration-150 hover:bg-accent-subtle/40 before:pointer-events-none before:absolute before:inset-y-0 before:left-0 before:w-0.5 before:bg-accent before:opacity-0 before:transition-opacity hover:before:opacity-100 focus-within:before:opacity-100 @xl:min-h-11 @xl:flex-row @xl:items-center @xl:gap-4 @xl:py-2">
      <div className="flex min-w-0 flex-1 items-baseline gap-3">
        <span className="w-16 shrink-0 font-mono text-xs whitespace-nowrap text-ink-subtle">{ticket.displayKey}</span>
        <Link
          to={ticketPath(ticket.projectKey, ticket.ticketNumber)}
          title={ticket.title}
          className="line-clamp-2 min-w-0 text-sm font-medium break-words text-ink outline-none after:absolute after:inset-0 after:content-[''] group-hover:text-accent-strong focus-visible:after:rounded-sm keyboard:focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-accent @xl:truncate"
        >
          {ticket.title}
        </Link>
      </div>
      {ticket.labels.length > 0 && (
        <div className="hidden max-w-64 shrink-0 @4xl:block">
          <LabelChips labels={ticket.labels} limit={2} />
        </div>
      )}
      <div className="flex shrink-0 items-center gap-3 text-xs text-ink-muted">
        <span className="@xl:w-24">
          <StatusBadge status={ticket.status} />
        </span>
        <span className="@xl:w-20">
          <PriorityLabel priority={ticket.priority} />
        </span>
        <span
          className={`inline-flex min-w-0 items-center gap-1.5 @xl:w-32 ${assignee ? '' : 'text-ink-subtle'}`}
          title={assignee ?? undefined}
        >
          {assignee && ticket.assigneeId ? (
            <Avatar name={assignee} seed={ticket.assigneeId} size="xs" />
          ) : (
            <EmptyAvatar size="xs" />
          )}
          <span className="min-w-0 truncate">
            <span className="sr-only">Assignee: </span>
            {assignee ?? 'Unassigned'}
          </span>
        </span>
        <time
          dateTime={ticket.updatedAt}
          title={time.full}
          className="ml-auto text-ink-subtle tabular-nums @xl:ml-0 @xl:w-10 @xl:text-right"
        >
          <span aria-hidden="true">{time.short}</span>
          <span className="sr-only">Updated {time.spoken}</span>
        </time>
      </div>
    </li>
  )
}
