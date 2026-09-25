import { ArrowRight, CircleDot, FileText, Flag, Pencil, Plus, Tag, UserRound, type LucideIcon } from 'lucide-react'

import type { Activity } from '../../../api/activities'
import { LoadError } from '../../../components/ui/LoadError'
import { formatRelativeTime } from '../../../lib/relativeTime'
import type { Resource } from '../../../lib/useResource'
import { useProjectContext } from '../../projects/projectContext'
import { describeActivity, type ActivityKind, type ActivitySegment } from '../activityText'

const BLOCK = 'rounded bg-line motion-safe:animate-pulse'

const ICONS: Record<ActivityKind, LucideIcon> = {
  created: Plus,
  title: Pencil,
  description: FileText,
  status: ArrowRight,
  priority: Flag,
  assignee: UserRound,
  label: Tag,
  other: CircleDot,
}

/**
 * The ticket's history, oldest first, read-only. The page reloads it after
 * each change of the ticket; if that reload fails, the entries shown stay
 * and a Retry is offered - the change itself was saved.
 */
export function TicketActivity({ activity }: { activity: Resource<Activity[]> }) {
  const { state } = activity
  return (
    <section aria-labelledby="ticket-activity" className="min-w-0 border-t border-line pt-5">
      <h3 id="ticket-activity" className="mb-3 text-sm font-semibold text-ink">
        Activity
      </h3>
      {(state.status === 'loading' || state.status === 'idle') && <ActivitySkeleton />}
      {state.status === 'error' && (
        <LoadError message="The activity could not be loaded." reason={state.reason} onRetry={activity.retry} />
      )}
      {state.status === 'ready' && (
        <>
          {state.refreshFailed && !state.refreshing && (
            <p role="alert" className="mb-2 text-xs text-danger">
              The activity could not be updated, so it may be out of date.{' '}
              <button
                type="button"
                onClick={activity.reload}
                className="font-medium text-accent underline-offset-4 hover:underline"
              >
                Retry
              </button>
            </p>
          )}
          {state.data.length === 0 ? (
            <p className="text-sm text-ink-subtle">No activity yet.</p>
          ) : (
            <ol aria-busy={state.refreshing} className="flex flex-col gap-1">
              {state.data.map((entry) => (
                <ActivityRow key={entry.id} entry={entry} now={state.receivedAt} />
              ))}
            </ol>
          )}
        </>
      )}
    </section>
  )
}

function ActivityRow({ entry, now }: { entry: Activity; now: number }) {
  const { memberName } = useProjectContext()
  const text = describeActivity(entry, memberName)
  const Icon = ICONS[text.kind]
  const time = formatRelativeTime(entry.createdAt, now)
  return (
    <li className="flex min-w-0 items-start gap-2.5 py-1">
      <Icon aria-hidden="true" className="mt-1 size-4 shrink-0 text-ink-subtle" strokeWidth={2} />
      <p className="min-w-0 flex-1 text-sm leading-6 break-words text-ink-muted">
        {text.segments.map((segment, index) => (
          <Segment key={index} segment={segment} />
        ))}
        <span aria-hidden="true" className="text-ink-subtle">
          {' · '}
        </span>
        <time dateTime={entry.createdAt} title={time.full} className="text-xs whitespace-nowrap text-ink-subtle tabular-nums">
          <span aria-hidden="true">{time.short}</span>
          <span className="sr-only">, {time.spoken}</span>
        </time>
      </p>
    </li>
  )
}

function Segment({ segment }: { segment: ActivitySegment }) {
  if (typeof segment === 'string') {
    return segment
  }
  if (segment.full === undefined) {
    return <span className="font-medium text-ink">{segment.value}</span>
  }
  // Shortened for display; assistive technology gets the whole value.
  return (
    <span className="font-medium text-ink" title={segment.full}>
      <span aria-hidden="true">{segment.value}</span>
      <span className="sr-only">{segment.full}</span>
    </span>
  )
}

function ActivitySkeleton() {
  return (
    <div aria-busy="true">
      <span className="sr-only" role="status">
        Loading activity…
      </span>
      <div aria-hidden="true" className="flex flex-col gap-3">
        {['w-1/2', 'w-2/3', 'w-2/5'].map((width) => (
          <div key={width} className="flex items-center gap-2.5">
            <div className={`size-4 shrink-0 ${BLOCK}`} />
            <div className={`h-3 ${width} ${BLOCK}`} />
          </div>
        ))}
      </div>
    </div>
  )
}
