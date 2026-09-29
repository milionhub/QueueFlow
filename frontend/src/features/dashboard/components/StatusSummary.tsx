import { Layers } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { DashboardStatusCount } from '../../../api/dashboard'
import { StatusIcon } from '../../tickets/TicketBadges'
import { STATUS_TONE } from '../../tickets/ticketDisplay'
import { SectionCard } from './SectionHeading'

interface StatusSummaryProps {
  statusCounts: DashboardStatusCount[]
  unassignedOpenCount: number
  className?: string
}

/**
 * The five statuses and their counts: a bar of their proportions (a picture
 * of the numbers below, so hidden from assistive technology) and the list
 * of counts itself. Nothing but the backend's own counts.
 */
export function StatusSummary({ statusCounts, unassignedOpenCount, className }: StatusSummaryProps) {
  const { t } = useTranslation(['dashboard', 'tickets'])
  const total = statusCounts.reduce((sum, { count }) => sum + count, 0)
  return (
    <SectionCard
      id="dashboard-status"
      title={t('status.title')}
      icon={Layers}
      tone="review"
      count={t('status.total', { count: total })}
      className={className}
      footer={unassignedOpenCount > 0 && t('status.unassigned', { count: unassignedOpenCount })}
    >
      <div className="px-4 pt-4 pb-3">
        <div aria-hidden="true" className="flex h-2.5 gap-0.5 overflow-hidden rounded-full bg-canvas-strong">
          {statusCounts
            .filter(({ count }) => count > 0)
            .map(({ status, count }) => (
              <span
                key={status}
                className={`min-w-1 first:rounded-l-full last:rounded-r-full ${STATUS_TONE[status].fill}`}
                style={{ flexGrow: count, flexBasis: 0 }}
              />
            ))}
        </div>
        <dl className="mt-3 grid grid-cols-1 gap-x-6 gap-y-0.5 min-[28rem]:grid-cols-2 xl:grid-cols-1">
          {statusCounts.map(({ status, count }) => (
            <div key={status} className="flex h-8 min-w-0 items-center gap-2 text-sm">
              <StatusIcon status={status} />
              <dt className="min-w-0 flex-1 truncate text-ink-muted">{t(`tickets:status.${status}`)}</dt>
              <dd className="flex items-center gap-2">
                <span className="w-9 text-right text-xs text-ink-subtle tabular-nums">
                  {total === 0 ? 0 : Math.round((count / total) * 100)}%
                </span>
                <span
                  className={`min-w-7 rounded-full px-2 text-center text-xs leading-5 font-semibold tabular-nums ${
                    count === 0 ? 'bg-canvas-strong text-ink-subtle' : STATUS_TONE[status].soft
                  }`}
                >
                  {count}
                </span>
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </SectionCard>
  )
}
