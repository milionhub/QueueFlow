import { AlignLeft, ChevronDown, FileSearch, SlidersHorizontal } from 'lucide-react'
import { useCallback, useId, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'

import { isNotFound } from '../../../api/errors'
import {
  updateTicket,
  type Ticket,
  type TicketPriority,
  type TicketStatus,
  type UpdateTicketRequest,
} from '../../../api/tickets'
import { Avatar, EmptyAvatar } from '../../../components/ui/Avatar'
import { buttonLinkClasses } from '../../../components/ui/buttonStyles'
import { LoadError } from '../../../components/ui/LoadError'
import { KeyBadge } from '../../../components/ui/PageHeader'
import { EmptyState, SkeletonFrame } from '../../../components/ui/States'
import { TextAreaField } from '../../../components/ui/TextAreaField'
import { TextField } from '../../../components/ui/TextField'
import { usePageTitle } from '../../../hooks/usePageTitle'
import { formatRelativeTime } from '../../../lib/relativeTime'
import { parseTicketNumber, projectPath } from '../../../routes/paths'
import { useAuth } from '../../auth/useAuth'
import { useProjectContext } from '../../projects/projectContext'
import { EditableTicketText } from '../components/EditableTicketText'
import { PropertySelect, type PropertyOption } from '../components/PropertySelect'
import { TicketActivity } from '../components/TicketActivity'
import { TicketComments } from '../components/TicketComments'
import { TicketLabels } from '../components/TicketLabels'
import { PriorityIcon, StatusIcon } from '../TicketBadges'
import { ticketChangeError } from '../ticketErrors'
import { TICKET_PRIORITIES, TICKET_STATUSES } from '../ticketDisplay'
import { useTicket } from '../useTicket'
import { useTicketActivities } from '../useTicketActivities'
import { useTicketMutation, type TicketMutation } from '../useTicketMutation'

const TITLE_MAX_LENGTH = 255

/** A blank description is no description. */
function normalizeDescription(value: string | null): string | null {
  const trimmed = value?.trim() ?? ''
  return trimmed === '' ? null : trimmed
}

/**
 * /app/projects/:projectKey/tickets/:ticketNumber. The project and members
 * come from ProjectLayout; only the ticket is loaded here. An address whose
 * number is not a positive integer is "not found" without asking the
 * backend. Every field is edited in place and saved on its own; the ticket
 * each save returns replaces the page's copy - no reload. The way back
 * (to the project's list, or its board when the ticket was opened there)
 * is in the shell's breadcrumbs.
 */
export function TicketDetailPage() {
  const { t } = useTranslation('tickets')
  const { project } = useProjectContext()
  const { ticketNumber: rawNumber } = useParams()
  const ticketNumber = parseTicketNumber(rawNumber)
  const { state, retry, replace } = useTicket(project.id, ticketNumber)
  const displayKey = `${project.key}-${rawNumber ?? ''}`

  const ready = state.status === 'ready' ? state : null
  const ticket = ready?.data ?? null
  usePageTitle(ticket ? ticket.displayKey : null, ticket ? `${ticket.displayKey} ${ticket.title}` : undefined)

  if (ticketNumber === null || (state.status === 'error' && isNotFound(state.error))) {
    return <TicketNotFound displayKey={displayKey} />
  }

  return (
    <div className="max-w-7xl">
      {state.status === 'error' && (
        <div className="flex flex-col gap-5">
          <h1 className="text-xl font-semibold tracking-tight text-ink">{displayKey}</h1>
          <LoadError message={t('detail.loadError')} reason={state.reason} onRetry={retry} />
        </div>
      )}
      {(state.status === 'loading' || state.status === 'idle') && <TicketDetailSkeleton displayKey={displayKey} />}
      {ready && (
        // Keyed by ticket: nothing of one ticket's editing carries over to another.
        <TicketDetail
          key={ready.data.id}
          ticket={ready.data}
          now={ready.receivedAt}
          replace={replace}
          onTicketGone={retry}
        />
      )}
    </div>
  )
}

interface TicketDetailProps {
  ticket: Ticket
  now: number
  replace: (ticket: Ticket) => void
  /** The backend says the ticket no longer exists: load it again, which shows "not found". */
  onTicketGone: () => void
}

/**
 * Key and title, then the properties, then the description, comments and
 * activity in one column. Below `xl` the properties are a compact row of
 * chips under the title, with the rest behind "More details"; from `xl`
 * they are a side panel. Comments and activity load alongside each other
 * once the ticket is known; every saved change of the ticket reloads the
 * activity, which the backend records.
 */
function TicketDetail({ ticket, now, replace, onTicketGone }: TicketDetailProps) {
  const { t } = useTranslation(['tickets', 'common'])
  const { authorizedRequest } = useAuth()
  const activity = useTicketActivities(ticket.id)
  const mutation = useTicketMutation(ticket.id, replace, onTicketGone, activity.reload)
  const [announcement, setAnnouncement] = useState('')
  // The same message twice in a row still changes the live region, so it is announced again.
  const announce = useCallback(
    (message: string) => setAnnouncement((current) => (current === message ? `${message} ` : message)),
    [],
  )

  /** Saves `changes`; resolves to an error message, or null when saved. */
  async function save(part: string, changes: UpdateTicketRequest, message: string): Promise<string | null> {
    const result = await mutation.run(part, () => updateTicket(authorizedRequest, ticket.id, changes))
    if (result === null) {
      return t('common:errors.busy')
    }
    if (!result.ok) {
      return ticketChangeError(result.error).message
    }
    announce(message)
    return null
  }

  async function saveTitle(value: string): Promise<string | null> {
    const title = value.trim()
    if (title === '') {
      return t('create.enterTitle')
    }
    if (title.length > TITLE_MAX_LENGTH) {
      return t('create.titleTooLong', { max: TITLE_MAX_LENGTH })
    }
    return title === ticket.title ? null : save('title', { title }, t('detail.titleUpdated'))
  }

  async function saveDescription(value: string): Promise<string | null> {
    const description = normalizeDescription(value)
    if (description === normalizeDescription(ticket.description)) {
      return null
    }
    return save(
      'description',
      { description },
      description === null ? t('detail.descriptionRemoved') : t('detail.descriptionUpdated'),
    )
  }

  const description = normalizeDescription(ticket.description)
  const titleClasses = 'text-xl leading-7 font-semibold tracking-tight break-words text-ink sm:text-2xl sm:leading-8'
  return (
    <div className="grid grid-cols-1 gap-x-10 gap-y-6 xl:grid-cols-[minmax(0,1fr)_19rem]">
      <header className="flex min-w-0 flex-col gap-2">
        <div className="flex items-center gap-2">
          <KeyBadge>{ticket.displayKey}</KeyBadge>
        </div>
        <EditableTicketText
          editLabel={t('detail.editTitle')}
          value={ticket.title}
          display={<h1 className={titleClasses}>{ticket.title}</h1>}
          whileEditing={<h1 className="sr-only">{ticket.title}</h1>}
          renderInput={({ value, onChange, onKeyDown, error, disabled }) => (
            <TextField
              label={t('fields.title')}
              labelHidden
              name="title"
              autoComplete="off"
              maxLength={TITLE_MAX_LENGTH}
              value={value}
              onChange={(event) => onChange(event.target.value)}
              onKeyDown={onKeyDown}
              error={error}
              disabled={disabled}
              required
            />
          )}
          onSave={saveTitle}
          saving={mutation.pending === 'title'}
          busy={mutation.pending !== null}
        />
      </header>
      <TicketProperties ticket={ticket} now={now} mutation={mutation} save={save} announce={announce} />
      <div className="flex min-w-0 flex-col gap-8">
        <section aria-labelledby="ticket-description" className="min-w-0">
          <EditableTicketText
            editLabel={t('detail.editDescription')}
            heading={
              <h2 id="ticket-description" className="flex items-center gap-2 text-sm font-semibold text-ink">
                <span
                  aria-hidden="true"
                  className="flex size-6 items-center justify-center rounded-md bg-accent-subtle text-accent"
                >
                  <AlignLeft className="size-3.5" strokeWidth={2.25} />
                </span>
                {t('fields.description')}
              </h2>
            }
            value={description ?? ''}
            emptyPrompt={t('detail.addDescription')}
            saveShortcut
            display={
              description && (
                <p className="text-[15px] leading-7 break-words whitespace-pre-wrap text-ink sm:text-sm sm:leading-6">
                  {description}
                </p>
              )
            }
            renderInput={({ value, onChange, onKeyDown, error, disabled }) => (
              <TextAreaField
                label={t('fields.description')}
                labelHidden
                name="description"
                rows={6}
                maxRows={20}
                value={value}
                onChange={(event) => onChange(event.target.value)}
                onKeyDown={onKeyDown}
                aria-keyshortcuts="Control+Enter Meta+Enter Escape"
                error={error}
                disabled={disabled}
              />
            )}
            onSave={saveDescription}
            saving={mutation.pending === 'description'}
            busy={mutation.pending !== null}
          />
        </section>
        <TicketComments ticketId={ticket.id} announce={announce} onTicketGone={onTicketGone} />
        <TicketActivity activity={activity} />
      </div>
      <p role="status" className="sr-only">
        {announcement}
      </p>
    </div>
  )
}

interface TicketPropertiesProps {
  ticket: Ticket
  now: number
  mutation: TicketMutation
  save: (part: string, changes: UpdateTicketRequest, announce: string) => Promise<string | null>
  announce: (message: string) => void
}

/**
 * Status, priority and assignee save as soon as they change; labels are
 * added and removed one by one; the rest never changes. One set of
 * controls for every screen size - only their layout changes - so nothing
 * is duplicated for assistive technology.
 */
function TicketProperties({ ticket, now, mutation, save, announce }: TicketPropertiesProps) {
  const { t } = useTranslation(['tickets', 'common'])
  const { members, memberName } = useProjectContext()
  const { user } = useAuth()
  const [moreOpen, setMoreOpen] = useState(false)
  const moreId = useId()
  const created = formatRelativeTime(ticket.createdAt, now)
  const updated = formatRelativeTime(ticket.updatedAt, now)
  const busy = mutation.pending !== null
  const statusOptions: PropertyOption[] = TICKET_STATUSES.map((value) => ({ value, label: t(`status.${value}`) }))
  const priorityOptions: PropertyOption[] = TICKET_PRIORITIES.map((value) => ({
    value,
    label: t(`priority.${value}`),
  }))
  const assigneeOptions: PropertyOption[] = [
    { value: '', label: t('common:people.unassigned') },
    ...members.map((member) => ({
      value: member.id,
      label: member.id === user?.id ? t('common:people.nameYou', { name: member.name }) : member.name,
    })),
  ]
  // An assignee missing from the loaded members still shows by id, never as "Unassigned".
  if (ticket.assigneeId && !members.some((member) => member.id === ticket.assigneeId)) {
    assigneeOptions.push({ value: ticket.assigneeId, label: memberName(ticket.assigneeId) })
  }
  const creator = memberName(ticket.creatorId)

  return (
    <section
      aria-labelledby="ticket-details"
      className="min-w-0 xl:col-start-2 xl:row-span-2 xl:row-start-1 xl:self-start xl:rounded-lg xl:border xl:border-line xl:bg-surface xl:px-3 xl:py-3 xl:shadow-xs"
    >
      <h2
        id="ticket-details"
        className="sr-only xl:not-sr-only xl:-mx-3 xl:-mt-3 xl:mb-2 xl:flex xl:items-center xl:gap-2 xl:rounded-t-lg xl:border-b xl:border-line xl:bg-canvas xl:px-4 xl:py-2.5 xl:text-sm xl:font-semibold xl:text-ink"
      >
        <SlidersHorizontal aria-hidden="true" className="hidden size-4 text-accent xl:block" strokeWidth={2} />
        {t('detail.details')}
      </h2>
      <dl className="flex flex-wrap items-start gap-2 xl:flex-col xl:flex-nowrap xl:items-stretch xl:gap-1">
        <PropertySelect
          label={t('fields.status')}
          value={ticket.status}
          options={statusOptions}
          icon={(value) => <StatusIcon status={value as TicketStatus} />}
          onSave={(value) =>
            save(
              'status',
              { status: value as TicketStatus },
              t('detail.statusChanged', { status: t(`status.${value as TicketStatus}`) }),
            )
          }
          saving={mutation.pending === 'status'}
          disabled={busy}
        />
        <PropertySelect
          label={t('fields.priority')}
          value={ticket.priority}
          options={priorityOptions}
          icon={(value) => <PriorityIcon priority={value as TicketPriority} />}
          onSave={(value) =>
            save(
              'priority',
              { priority: value as TicketPriority },
              t('detail.priorityChanged', { priority: t(`priority.${value as TicketPriority}`) }),
            )
          }
          saving={mutation.pending === 'priority'}
          disabled={busy}
        />
        <PropertySelect
          label={t('fields.assignee')}
          value={ticket.assigneeId ?? ''}
          options={assigneeOptions}
          icon={(value) =>
            value ? <Avatar name={memberName(value)} seed={value} size="xs" /> : <EmptyAvatar size="xs" />
          }
          onSave={(value) =>
            save(
              'assignee',
              { assigneeId: value === '' ? null : value },
              value === '' ? t('detail.unassigned') : t('detail.assignedTo', { name: memberName(value) }),
            )
          }
          saving={mutation.pending === 'assignee'}
          disabled={busy}
        />
        <Property term={t('fields.labels')} wide>
          <TicketLabels ticket={ticket} mutation={mutation} announce={announce} />
        </Property>
      </dl>

      {/* Below `xl`: who and when, behind a disclosure. From `xl`: always shown. */}
      <div className="mt-3 xl:mt-2 xl:border-t xl:border-line xl:pt-2">
        <button
          type="button"
          aria-expanded={moreOpen}
          aria-controls={moreId}
          onClick={() => setMoreOpen((open) => !open)}
          className="press -ml-2 inline-flex h-9 items-center gap-1 rounded-md px-2 text-xs font-medium text-ink-muted transition-colors hover:bg-canvas-strong hover:text-ink xl:hidden"
        >
          {t('detail.moreDetails')}
          <ChevronDown
            aria-hidden="true"
            className={`size-3.5 transition-transform duration-150 ${moreOpen ? 'rotate-180' : ''}`}
            strokeWidth={2}
          />
        </button>
        <dl
          id={moreId}
          className={`${moreOpen ? 'grid animate-fade-in' : 'hidden'} gap-1 rounded-lg border border-line bg-surface px-3 py-2 xl:grid xl:rounded-none xl:border-0 xl:bg-transparent xl:p-0`}
        >
          <Property term={t('detail.createdBy')}>
            <span className="inline-flex min-w-0 items-center gap-2">
              <Avatar name={creator} seed={ticket.creatorId} size="xs" />
              <span className="truncate">{creator}</span>
            </span>
          </Property>
          <Property term={t('detail.created')}>
            <time dateTime={ticket.createdAt} title={created.full}>
              {created.full}
            </time>
          </Property>
          <Property term={t('detail.updated')}>
            <time dateTime={ticket.updatedAt} title={updated.full}>
              {updated.full}
            </time>
          </Property>
        </dl>
      </div>
    </section>
  )
}

/** A read-only (or composite) property: label and value side by side from `xl`, stacked below it. */
function Property({ term, wide = false, children }: { term: string; wide?: boolean; children: ReactNode }) {
  return (
    <div
      className={`grid min-w-0 grid-cols-[5.5rem_minmax(0,1fr)] items-center gap-x-2 py-1 xl:min-h-8 ${
        wide ? 'w-full items-start max-xl:grid-cols-1 xl:items-start xl:pt-1.5' : ''
      }`}
    >
      <dt className={`text-xs font-medium text-ink-muted ${wide ? 'max-xl:sr-only xl:pt-1' : ''}`}>{term}</dt>
      <dd className="min-w-0 text-sm text-ink">{children}</dd>
    </div>
  )
}

/** Unknown number, invalid address, or another workspace's ticket: the same answer. */
function TicketNotFound({ displayKey }: { displayKey: string }) {
  const { t } = useTranslation('tickets')
  const { project } = useProjectContext()
  usePageTitle(t('detail.notFoundDocument'))
  return (
    <EmptyState
      as="h1"
      icon={FileSearch}
      title={t('detail.notFoundTitle', { key: displayKey })}
      className="max-w-xl"
      action={
        <Link to={projectPath(project.key)} className={buttonLinkClasses('secondary')}>
          {t('detail.backToTickets', { key: project.key })}
        </Link>
      }
    >
      {t('detail.notFoundBody', { name: project.name })}
    </EmptyState>
  )
}

/** The page's own shape while the ticket loads; the key is known from the address already. */
function TicketDetailSkeleton({ displayKey }: { displayKey: string }) {
  const { t } = useTranslation('tickets')
  return (
    <div className="grid grid-cols-1 gap-x-10 gap-y-6 xl:grid-cols-[minmax(0,1fr)_19rem]">
      <div className="flex flex-col gap-2">
        <div className="flex">
          <KeyBadge>{displayKey}</KeyBadge>
        </div>
        <h1 className="sr-only">{displayKey}</h1>
        <SkeletonFrame label={t('detail.loading')}>
          <div className="flex flex-col gap-6">
            <div className="skeleton h-7 w-3/4" />
            <div className="flex flex-wrap gap-2 xl:hidden">
              {['w-28', 'w-24', 'w-32'].map((width) => (
                <div key={width} className={`skeleton h-9 ${width} rounded-full!`} />
              ))}
            </div>
            <div className="flex flex-col gap-2.5">
              <div className="skeleton h-3.5 w-24" />
              <div className="skeleton h-3 w-full" />
              <div className="skeleton h-3 w-11/12" />
              <div className="skeleton h-3 w-2/3" />
            </div>
          </div>
        </SkeletonFrame>
      </div>
      <div
        aria-hidden="true"
        className="skeleton-delay hidden flex-col gap-3 rounded-lg border border-line bg-surface p-4 shadow-xs xl:row-span-2 xl:flex"
      >
        {[1, 2, 3, 4].map((row) => (
          <div key={row} className="flex items-center gap-3">
            <div className="skeleton h-3 w-16" />
            <div className="skeleton h-3 flex-1" />
          </div>
        ))}
      </div>
    </div>
  )
}
