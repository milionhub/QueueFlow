import { useState, type ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'

import { isNotFound } from '../../../api/errors'
import {
  updateTicket,
  type Ticket,
  type TicketPriority,
  type TicketStatus,
  type UpdateTicketRequest,
} from '../../../api/tickets'
import { LoadError } from '../../../components/ui/LoadError'
import { TextAreaField } from '../../../components/ui/TextAreaField'
import { TextField } from '../../../components/ui/TextField'
import { usePageTitle } from '../../../hooks/usePageTitle'
import { formatRelativeTime } from '../../../lib/relativeTime'
import { isFromBoard, parseTicketNumber, projectBoardPath, projectPath } from '../../../routes/paths'
import { useAuth } from '../../auth/useAuth'
import { useProjectContext } from '../../projects/projectContext'
import { EditableTicketText } from '../components/EditableTicketText'
import { TicketLabels } from '../components/TicketLabels'
import { PropertySelect, type PropertyOption } from '../components/PropertySelect'
import { ticketChangeError } from '../ticketErrors'
import { PRIORITY_LABELS, STATUS_LABELS } from '../ticketDisplay'
import { useTicket } from '../useTicket'
import { useTicketMutation, type TicketMutation } from '../useTicketMutation'

const BLOCK = 'rounded bg-line motion-safe:animate-pulse'
const TITLE_MAX_LENGTH = 255
const STATUS_OPTIONS: PropertyOption[] = (Object.keys(STATUS_LABELS) as TicketStatus[]).map((value) => ({
  value,
  label: STATUS_LABELS[value],
}))
const PRIORITY_OPTIONS: PropertyOption[] = (Object.keys(PRIORITY_LABELS) as TicketPriority[]).map((value) => ({
  value,
  label: PRIORITY_LABELS[value],
}))

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
 * each save returns replaces the page's copy - no reload.
 */
export function TicketDetailPage() {
  const { project } = useProjectContext()
  const { ticketNumber: rawNumber } = useParams()
  const ticketNumber = parseTicketNumber(rawNumber)
  const { state, retry, replace } = useTicket(project.id, ticketNumber)
  const displayKey = `${project.key}-${rawNumber ?? ''}`

  const ready = state.status === 'ready' ? state : null
  const ticket = ready?.data ?? null
  usePageTitle(
    ticket ? ticket.displayKey : null,
    ticket ? `${ticket.displayKey} ${ticket.title}` : undefined,
  )

  if (ticketNumber === null || (state.status === 'error' && isNotFound(state.error))) {
    return <TicketNotFound displayKey={displayKey} />
  }

  return (
    <div className="flex max-w-6xl flex-col gap-5">
      <BackToProject />
      {state.status === 'error' && (
        <LoadError message="The ticket could not be loaded." reason={state.reason} onRetry={retry} />
      )}
      {(state.status === 'loading' || state.status === 'idle') && <TicketDetailSkeleton />}
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

const BACK_LINK = 'w-fit max-w-full truncate text-sm text-ink-muted underline-offset-4 hover:text-ink hover:underline'

/** Back to the board when the ticket was opened from it; otherwise to the project's ticket list. */
function BackToProject() {
  const { project } = useProjectContext()
  const location = useLocation()
  if (isFromBoard(location.state)) {
    return (
      <Link to={projectBoardPath(project.key)} className={BACK_LINK}>
        <span aria-hidden="true">← </span>
        Back to <span className="font-mono text-xs">{project.key}</span> board
      </Link>
    )
  }
  return (
    <Link to={projectPath(project.key)} className={BACK_LINK}>
      <span aria-hidden="true">← </span>
      <span className="font-mono text-xs">{project.key}</span> · {project.name}
      <span className="sr-only"> (back to the project's tickets)</span>
    </Link>
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
 * Title, then the details, then the description in one column; from `xl`
 * the details move to a side column. The space under the description is
 * where comments and activity will go.
 */
function TicketDetail({ ticket, now, replace, onTicketGone }: TicketDetailProps) {
  const { authorizedRequest } = useAuth()
  const mutation = useTicketMutation(ticket.id, replace, onTicketGone)
  const [announcement, setAnnouncement] = useState('')

  /** Saves `changes`; resolves to an error message, or null when saved. */
  async function save(part: string, changes: UpdateTicketRequest, announce: string): Promise<string | null> {
    const result = await mutation.run(part, () => updateTicket(authorizedRequest, ticket.id, changes))
    if (result === null) {
      return 'Another change is still being saved. Try again in a moment.'
    }
    if (!result.ok) {
      return ticketChangeError(result.error).message
    }
    setAnnouncement(announce)
    return null
  }

  async function saveTitle(value: string): Promise<string | null> {
    const title = value.trim()
    if (title === '') {
      return 'Enter a title.'
    }
    if (title.length > TITLE_MAX_LENGTH) {
      return `Title must be at most ${TITLE_MAX_LENGTH} characters.`
    }
    return title === ticket.title ? null : save('title', { title }, 'Title updated.')
  }

  async function saveDescription(value: string): Promise<string | null> {
    const description = normalizeDescription(value)
    if (description === normalizeDescription(ticket.description)) {
      return null
    }
    return save('description', { description }, description === null ? 'Description removed.' : 'Description updated.')
  }

  const description = normalizeDescription(ticket.description)
  return (
    <div className="grid grid-cols-1 gap-x-8 gap-y-6 xl:grid-cols-[minmax(0,1fr)_20rem] xl:grid-rows-[auto_1fr]">
      <EditableTicketText
        field="title"
        value={ticket.title}
        display={<h2 className="text-xl leading-8 font-semibold tracking-tight break-words text-ink">{ticket.title}</h2>}
        renderInput={({ value, onChange, onKeyDown, error, disabled }) => (
          <TextField
            label="Title"
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
      <TicketProperties ticket={ticket} now={now} mutation={mutation} save={save} announce={setAnnouncement} />
      <section aria-labelledby="ticket-description" className="min-w-0">
        <h3 id="ticket-description" className="mb-2 text-sm font-semibold text-ink">
          Description
        </h3>
        <EditableTicketText
          field="description"
          value={description ?? ''}
          display={
            description ? (
              <p className="text-sm leading-6 break-words whitespace-pre-wrap text-ink">{description}</p>
            ) : (
              <p className="text-sm text-ink-subtle">No description.</p>
            )
          }
          renderInput={({ value, onChange, onKeyDown, error, disabled }) => (
            <TextAreaField
              label="Description"
              name="description"
              rows={8}
              value={value}
              onChange={(event) => onChange(event.target.value)}
              onKeyDown={onKeyDown}
              error={error}
              disabled={disabled}
            />
          )}
          onSave={saveDescription}
          saving={mutation.pending === 'description'}
          busy={mutation.pending !== null}
          layout="stacked"
        />
      </section>
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
 * added and removed one by one; the rest never changes.
 */
function TicketProperties({ ticket, now, mutation, save, announce }: TicketPropertiesProps) {
  const { members, memberName } = useProjectContext()
  const { user } = useAuth()
  const created = formatRelativeTime(ticket.createdAt, now)
  const updated = formatRelativeTime(ticket.updatedAt, now)
  const busy = mutation.pending !== null
  const assigneeOptions: PropertyOption[] = [
    { value: '', label: 'Unassigned' },
    ...members.map((member) => ({
      value: member.id,
      label: member.id === user?.id ? `${member.name} (you)` : member.name,
    })),
  ]
  // An assignee missing from the loaded members still shows by id, never as "Unassigned".
  if (ticket.assigneeId && !members.some((member) => member.id === ticket.assigneeId)) {
    assigneeOptions.push({ value: ticket.assigneeId, label: memberName(ticket.assigneeId) })
  }

  return (
    <section
      aria-labelledby="ticket-details"
      className="rounded-md border border-line bg-surface px-4 py-4 xl:col-start-2 xl:row-span-2 xl:row-start-1 xl:self-start"
    >
      <h3 id="ticket-details" className="text-sm font-semibold text-ink">
        Details
      </h3>
      <dl className="mt-3 grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2 xl:grid-cols-1">
        <PropertySelect
          label="Status"
          value={ticket.status}
          options={STATUS_OPTIONS}
          onSave={(value) =>
            save(
              'status',
              { status: value as TicketStatus },
              `Status changed to ${STATUS_LABELS[value as TicketStatus]}.`,
            )
          }
          saving={mutation.pending === 'status'}
          disabled={busy}
        />
        <PropertySelect
          label="Priority"
          value={ticket.priority}
          options={PRIORITY_OPTIONS}
          onSave={(value) =>
            save(
              'priority',
              { priority: value as TicketPriority },
              `Priority changed to ${PRIORITY_LABELS[value as TicketPriority]}.`,
            )
          }
          saving={mutation.pending === 'priority'}
          disabled={busy}
        />
        <PropertySelect
          label="Assignee"
          value={ticket.assigneeId ?? ''}
          options={assigneeOptions}
          onSave={(value) =>
            save(
              'assignee',
              { assigneeId: value === '' ? null : value },
              value === '' ? 'Ticket unassigned.' : `Assigned to ${memberName(value)}.`,
            )
          }
          saving={mutation.pending === 'assignee'}
          disabled={busy}
        />
        <Property term="Labels">
          <TicketLabels ticket={ticket} mutation={mutation} announce={announce} />
        </Property>
        <Property term="Created by">
          <span className="break-words">{memberName(ticket.creatorId)}</span>
        </Property>
        <Property term="Created">
          <time dateTime={ticket.createdAt} title={created.full}>
            {created.full}
          </time>
        </Property>
        <Property term="Updated">
          <time dateTime={ticket.updatedAt} title={updated.full}>
            {updated.full}
          </time>
        </Property>
      </dl>
    </section>
  )
}

function Property({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <dt className="text-xs font-medium text-ink-subtle">{term}</dt>
      <dd className="min-w-0 text-sm text-ink">{children}</dd>
    </div>
  )
}

/** Unknown number, invalid address, or another workspace's ticket: the same answer. */
function TicketNotFound({ displayKey }: { displayKey: string }) {
  const { project } = useProjectContext()
  usePageTitle('Ticket not found')
  return (
    <section aria-labelledby="ticket-not-found" className="max-w-lg rounded-md border border-line bg-surface px-5 py-4">
      <h2 id="ticket-not-found" className="text-sm font-semibold break-words text-ink">
        Ticket {displayKey} not found
      </h2>
      <p className="mt-1.5 text-sm leading-6 text-ink-muted">
        There is no such ticket in {project.name}.{' '}
        <Link to={projectPath(project.key)} className="font-medium text-accent underline-offset-4 hover:underline">
          Back to {project.key} tickets
        </Link>
      </p>
    </section>
  )
}

function TicketDetailSkeleton() {
  return (
    <div aria-busy="true">
      <span className="sr-only" role="status">
        Loading ticket…
      </span>
      <div aria-hidden="true" className="grid grid-cols-1 gap-x-8 gap-y-6 xl:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="flex flex-col gap-4">
          <div className={`h-6 w-3/4 ${BLOCK}`} />
          <div className={`h-3 w-full ${BLOCK}`} />
          <div className={`h-3 w-5/6 ${BLOCK}`} />
        </div>
        <div className="h-64 rounded-md border border-line bg-surface" />
      </div>
    </div>
  )
}
