import type { DashboardTicket } from '../../../api/dashboard'
import { formatRelativeTime } from '../../../lib/relativeTime'
import { PriorityLabel, StatusBadge } from '../../tickets/TicketBadges'

interface TicketListProps {
  tickets: DashboardTicket[]
  /** Which details follow the title: the priority (my tickets) or the assignee (recent changes). */
  detail: 'priority' | 'assignee'
  /** When the data arrived: every relative time in the page is measured from this one moment. */
  now: number
}

/**
 * Ticket rows: key and title, then the details. One line when the list
 * is at least 32rem wide (a container query, so it adapts to the column,
 * not the window), two lines below. Not links: there is no ticket page yet.
 */
export function TicketList({ tickets, detail, now }: TicketListProps) {
  return (
    <div className="@container">
      <ul className="divide-y divide-line rounded-md border border-line bg-surface">
        {tickets.map((ticket) => (
          <TicketRow key={ticket.id} ticket={ticket} detail={detail} now={now} />
        ))}
      </ul>
    </div>
  )
}

function TicketRow({ ticket, detail, now }: { ticket: DashboardTicket; detail: TicketListProps['detail']; now: number }) {
  const time = formatRelativeTime(ticket.updatedAt, now)
  return (
    <li className="flex flex-col gap-1 px-4 py-2.5 @lg:flex-row @lg:items-center @lg:gap-4">
      <div className="flex min-w-0 flex-1 items-baseline gap-3">
        <span className="min-w-14 shrink-0 font-mono text-xs whitespace-nowrap text-ink-subtle">{ticket.displayKey}</span>
        <span className="min-w-0 truncate text-sm text-ink" title={ticket.title}>
          {ticket.title}
        </span>
      </div>
      <div className="flex shrink-0 items-center gap-3 text-xs text-ink-muted">
        {detail === 'priority' ? (
          <span className="@lg:w-18">
            <PriorityLabel priority={ticket.priority} />
          </span>
        ) : (
          <span
            className={`max-w-40 truncate @lg:w-28 ${ticket.assigneeName ? '' : 'text-ink-subtle'}`}
            title={ticket.assigneeName ?? undefined}
          >
            <span className="sr-only">Assignee: </span>
            {ticket.assigneeName ?? 'Unassigned'}
          </span>
        )}
        <span className="@lg:w-24">
          <StatusBadge status={ticket.status} />
        </span>
        <time dateTime={ticket.updatedAt} title={time.full} className="ml-auto tabular-nums @lg:ml-0 @lg:w-12 @lg:text-right">
          <span aria-hidden="true">{time.short}</span>
          <span className="sr-only">Updated {time.spoken}</span>
        </time>
      </div>
    </li>
  )
}
