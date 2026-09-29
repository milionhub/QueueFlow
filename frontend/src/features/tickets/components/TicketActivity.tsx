import {
  ArrowRight,
  ChevronsUpDown,
  CircleDot,
  FileText,
  Flag,
  History,
  Pencil,
  Plus,
  Tag,
  UserRound,
  type LucideIcon,
} from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { Activity } from '../../../api/activities'
import type { TicketStatus } from '../../../api/tickets'
import { LoadError } from '../../../components/ui/LoadError'
import { SkeletonFrame, StaleNotice } from '../../../components/ui/States'
import { i18n } from '../../../i18n'
import { dateFormat, formatRelativeTime } from '../../../lib/relativeTime'
import type { Resource } from '../../../lib/useResource'
import { useProjectContext } from '../../projects/projectContext'
import { describeActivity, type ActivityKind, type ActivitySegment } from '../activityText'
import { STATUS_TONE } from '../ticketDisplay'

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

/** Creation and status changes carry a little more weight; the rest of the history stays quiet. */
const ICON_TONE: Partial<Record<ActivityKind, string>> = {
  created: 'bg-success-subtle text-success',
  status: 'bg-accent-subtle text-accent',
}

/** A status change takes the hue of the status it moved to (its words still say which). */
function iconTone(kind: ActivityKind, newValue: string | null): string {
  if (kind === 'status' && newValue && newValue in STATUS_TONE) {
    return STATUS_TONE[newValue as TicketStatus].soft
  }
  return ICON_TONE[kind] ?? 'bg-canvas-strong text-ink-subtle'
}

/** How many of the latest entries show before "Show earlier". */
const COLLAPSED_COUNT = 5
/** Consecutive entries by one person within this time share one name. */
const GROUP_WINDOW = 2 * 60_000


function dayKey(iso: string): string {
  const date = new Date(iso)
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

/** "Today", "Yesterday", "Tue, Sep 23", or with the year when it is not this one - in the interface language. */
function dayLabel(iso: string, now: number): string {
  const date = new Date(iso)
  const today = new Date(now)
  const yesterday = new Date(now)
  yesterday.setDate(today.getDate() - 1)
  if (dayKey(iso) === dayKey(today.toISOString())) {
    return i18n.t('common:time.today')
  }
  if (dayKey(iso) === dayKey(yesterday.toISOString())) {
    return i18n.t('common:time.yesterday')
  }
  const format =
    date.getFullYear() === today.getFullYear()
      ? dateFormat({ weekday: 'short', month: 'short', day: 'numeric' })
      : dateFormat({ month: 'short', day: 'numeric', year: 'numeric' })
  return format.format(date)
}

/**
 * The ticket's history, oldest first, read-only, as compact rows. Only the
 * latest five show at first; "Show N earlier events" reveals the rest,
 * with a line for each day. The page reloads it after each change of the
 * ticket; if that reload fails, the entries shown stay and a Retry is
 * offered - the change itself was saved.
 */
export function TicketActivity({ activity }: { activity: Resource<Activity[]> }) {
  const { t } = useTranslation(['activity', 'common'])
  const { state } = activity
  const [expanded, setExpanded] = useState(false)
  const listId = useId()
  return (
    <section aria-labelledby="ticket-activity" className="min-w-0 border-t border-line pt-6">
      <h2 id="ticket-activity" className="mb-3 flex items-center gap-2 text-sm font-semibold text-ink">
        <span
          aria-hidden="true"
          className="flex size-6 items-center justify-center rounded-md bg-accent-subtle text-accent"
        >
          <History className="size-3.5" strokeWidth={2.25} />
        </span>
        {t('title')}
      </h2>
      {(state.status === 'loading' || state.status === 'idle') && <ActivitySkeleton />}
      {state.status === 'error' && (
        <LoadError
          message={t('loadError')}
          reason={state.reason}
          onRetry={activity.retry}
          size="inline"
        />
      )}
      {state.status === 'ready' && (
        <>
          {state.refreshFailed && !state.refreshing && (
            <div role="alert" className="mb-2">
              <StaleNotice onRefresh={activity.reload} label={t('common:actions.retry')}>
                {t('stale')}
              </StaleNotice>
            </div>
          )}
          {state.data.length === 0 ? (
            <p className="text-sm text-ink-subtle">{t('empty')}</p>
          ) : (
            <ActivityList
              entries={state.data}
              now={state.receivedAt}
              refreshing={state.refreshing}
              expanded={expanded}
              onToggle={() => setExpanded((current) => !current)}
              listId={listId}
            />
          )}
        </>
      )}
    </section>
  )
}

interface ActivityListProps {
  entries: Activity[]
  now: number
  refreshing: boolean
  expanded: boolean
  onToggle: () => void
  listId: string
}

function ActivityList({ entries, now, refreshing, expanded, onToggle, listId }: ActivityListProps) {
  const { t } = useTranslation('activity')
  const hidden = expanded ? 0 : Math.max(0, entries.length - COLLAPSED_COUNT)
  const shown = entries.slice(hidden)
  const canCollapse = entries.length > COLLAPSED_COUNT
  return (
    <div className="flex flex-col gap-1">
      {canCollapse && (
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={listId}
          onClick={onToggle}
          className="press -ml-2 inline-flex h-8 w-fit items-center gap-1.5 rounded-md px-2 text-xs font-medium text-ink-muted transition-colors hover:bg-canvas-strong hover:text-ink pointer-coarse:h-10"
        >
          <ChevronsUpDown aria-hidden="true" className="size-3.5" strokeWidth={2} />
          {expanded ? t('showRecent') : t('showEarlier', { count: hidden })}
        </button>
      )}
      <ol id={listId} aria-busy={refreshing} className="relative flex flex-col">
        {/* The thread that joins the entries' icons. */}
        <span aria-hidden="true" className="absolute top-3 bottom-3 left-[11px] w-px bg-line" />
        {shown.map((entry, index) => {
          // The first row shown always names who did it, even when earlier entries are hidden.
          const previous = index > 0 ? shown[index - 1] : undefined
          const newDay = expanded && (!previous || dayKey(previous.createdAt) !== dayKey(entry.createdAt))
          const sameActor =
            previous !== undefined &&
            !newDay &&
            previous.userId === entry.userId &&
            Date.parse(entry.createdAt) - Date.parse(previous.createdAt) <= GROUP_WINDOW
          return (
            <ActivityRow
              key={entry.id}
              entry={entry}
              now={now}
              day={newDay ? dayLabel(entry.createdAt, now) : null}
              sameActor={sameActor}
            />
          )
        })}
      </ol>
    </div>
  )
}

interface ActivityRowProps {
  entry: Activity
  now: number
  /** The first entry of a day, when the whole history shows: the day's name above it. */
  day: string | null
  /** Same person as the entry above, moments later: their name is not repeated (it is still read out). */
  sameActor: boolean
}

function ActivityRow({ entry, now, day, sameActor }: ActivityRowProps) {
  // Subscribes the row to the interface language: its sentence is worded while rendering.
  useTranslation()
  const { memberName } = useProjectContext()
  const text = describeActivity(entry, memberName)
  const Icon = ICONS[text.kind]
  const time = formatRelativeTime(entry.createdAt, now)
  return (
    <li className="relative min-w-0">
      {day && (
        <p className="relative ml-8 pt-3 pb-1 text-[11px] font-semibold tracking-wide text-ink-subtle uppercase">
          {day}
        </p>
      )}
      <div className="flex min-h-8 items-start gap-2.5 py-1">
        <span
          aria-hidden="true"
          className={`relative mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full ring-4 ring-canvas ${iconTone(
            text.kind,
            entry.newValue,
          )}`}
        >
          <Icon className="size-3.5" strokeWidth={2} />
        </span>
        <p className="min-w-0 flex-1 pt-0.5 text-[13px] leading-5 break-words text-ink-muted">
          {text.segments.map((segment, index) =>
            sameActor && typeof segment !== 'string' && segment.actor ? (
              <span key={index} className="sr-only">
                <Segment segment={segment} />
              </span>
            ) : (
              <Segment key={index} segment={segment} />
            ),
          )}
          <span aria-hidden="true" className="text-ink-subtle">
            {' · '}
          </span>
          <time
            dateTime={entry.createdAt}
            title={time.full}
            className="text-xs whitespace-nowrap text-ink-subtle tabular-nums"
          >
            <span aria-hidden="true">{time.short}</span>
            <span className="sr-only">, {time.spoken}</span>
          </time>
        </p>
      </div>
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
  const { t } = useTranslation('activity')
  return (
    <SkeletonFrame label={t('loading')}>
      <div className="flex flex-col gap-3">
        {['w-1/2', 'w-2/3', 'w-2/5'].map((width) => (
          <div key={width} className="flex items-center gap-2.5">
            <div className="skeleton size-6 shrink-0 rounded-full!" />
            <div className={`skeleton h-3 ${width}`} />
          </div>
        ))}
      </div>
    </SkeletonFrame>
  )
}
