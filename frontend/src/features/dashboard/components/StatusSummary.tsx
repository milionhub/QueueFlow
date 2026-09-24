import type { DashboardStatusCount } from '../../../api/dashboard'
import { STATUS_LABELS } from '../../tickets/ticketDisplay'
import { SectionHeading } from './SectionHeading'

interface StatusSummaryProps {
  statusCounts: DashboardStatusCount[]
  unassignedOpenCount: number
}

/**
 * The five statuses and their counts in one strip: two columns on phones,
 * one row from `sm` up.
 */
export function StatusSummary({ statusCounts, unassignedOpenCount }: StatusSummaryProps) {
  return (
    <section aria-labelledby="dashboard-status">
      <SectionHeading id="dashboard-status" title="Tickets by status">
        {unassignedOpenCount > 0 && (
          <span>
            {unassignedOpenCount} open {unassignedOpenCount === 1 ? 'ticket' : 'tickets'} unassigned
          </span>
        )}
      </SectionHeading>
      <dl className="grid grid-cols-2 rounded-md border border-line bg-surface sm:grid-cols-5 sm:divide-x sm:divide-line">
        {statusCounts.map(({ status, count }) => (
          <div key={status} className="flex min-w-0 items-baseline justify-between gap-3 px-4 py-2.5">
            <dt className="truncate text-sm text-ink-muted">{STATUS_LABELS[status]}</dt>
            <dd className="text-base font-semibold text-ink tabular-nums">{count}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
