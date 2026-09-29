import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import type { DashboardTicket } from '../../../api/dashboard'
import { Avatar, EmptyAvatar } from '../../../components/ui/Avatar'
import { formatRelativeTime } from '../../../lib/relativeTime'
import { ticketPathFromDisplayKey } from '../../../routes/paths'
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
 * not the window), two lines below. The whole row is the link to the
 * ticket (its title is the link's text).
 */
export function TicketList({ tickets, detail, now }: TicketListProps) {
  return (
    <div className="@container">
      <ul className="divide-y divide-line">
        {tickets.map((ticket) => (
          <TicketRow key={ticket.id} ticket={ticket} detail={detail} now={now} />
        ))}
      </ul>
    </div>
  )
}

function TicketRow({
  ticket,
  detail,
  now,
}: {
  ticket: DashboardTicket
  detail: TicketListProps['detail']
  now: number
}) {
  const { t } = useTranslation(['tickets', 'common'])
  const time = formatRelativeTime(ticket.updatedAt, now)
  return (
    <li className="group relative flex flex-col gap-1.5 px-4 py-3 transition-colors duration-150 hover:bg-accent-subtle/40 before:pointer-events-none before:absolute before:inset-y-0 before:left-0 before:w-0.5 before:bg-accent before:opacity-0 before:transition-opacity hover:before:opacity-100 focus-within:before:opacity-100 @lg:min-h-11 @lg:flex-row @lg:items-center @lg:gap-4 @lg:py-2">
      <div className="flex min-w-0 flex-1 items-baseline gap-3">
        <span className="w-16 shrink-0 font-mono text-xs whitespace-nowrap text-ink-subtle">{ticket.displayKey}</span>
        <Link
          to={ticketPathFromDisplayKey(ticket.displayKey)}
          title={ticket.title}
          className="line-clamp-2 min-w-0 text-sm font-medium break-words text-ink outline-none @lg:truncate after:absolute after:inset-0 after:content-[''] group-hover:text-accent-strong focus-visible:after:rounded-sm keyboard:focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-accent"
        >
          {ticket.title}
        </Link>
      </div>
      <div className="flex shrink-0 items-center gap-3 text-xs text-ink-muted">
        {detail === 'priority' ? (
          <span className="@lg:w-20">
            <PriorityLabel priority={ticket.priority} />
          </span>
        ) : (
          <span
            className={`inline-flex max-w-40 min-w-0 items-center gap-1.5 @lg:w-32 ${ticket.assigneeName ? '' : 'text-ink-subtle'}`}
            title={ticket.assigneeName ?? undefined}
          >
            {ticket.assigneeName && ticket.assigneeId ? (
              <Avatar name={ticket.assigneeName} seed={ticket.assigneeId} size="xs" />
            ) : (
              <EmptyAvatar size="xs" />
            )}
            <span className="truncate">
              <span className="sr-only">{t('row.assigneePrefix')}</span>
              {ticket.assigneeName ?? t('common:people.unassigned')}
            </span>
          </span>
        )}
        <span className="@lg:w-24">
          <StatusBadge status={ticket.status} />
        </span>
        <time
          dateTime={ticket.updatedAt}
          title={time.full}
          className="ml-auto text-ink-subtle tabular-nums @lg:ml-0 @lg:w-10 @lg:text-right"
        >
          <span aria-hidden="true">{time.short}</span>
          <span className="sr-only">{t('row.updated', { time: time.spoken })}</span>
        </time>
      </div>
    </li>
  )
}
