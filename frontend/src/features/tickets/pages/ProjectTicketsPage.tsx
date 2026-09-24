import { useEffect, useId, useMemo, useRef, useState, type MouseEvent } from 'react'
import { useSearchParams } from 'react-router'

import type { Ticket } from '../../../api/tickets'
import { Button } from '../../../components/ui/Button'
import { LoadError } from '../../../components/ui/LoadError'
import { usePageTitle } from '../../../hooks/usePageTitle'
import { useAuth } from '../../auth/useAuth'
import { useProjectContext } from '../../projects/projectContext'
import { CreateTicketDialog } from '../components/CreateTicketDialog'
import { ProjectTicketList } from '../components/ProjectTicketList'
import { TicketFilterBar } from '../components/TicketFilterBar'
import {
  filterTickets,
  hasActiveFilters,
  labelsInTickets,
  readTicketFilters,
  withFilter,
  withoutFilters,
  type FilterParam,
} from '../ticketFilters'
import { useProjectTickets } from '../useProjectTickets'

const BLOCK = 'rounded bg-line motion-safe:animate-pulse'

/**
 * /app/projects/:projectKey: the project's page is its ticket list. The
 * shell header shows the project's name; the key, description and counts
 * sit above the list. Anyone in the workspace can create tickets; after a
 * create the list reloads, so the order stays the backend's. Search and
 * filters live in the address and narrow the loaded list without requests.
 */
export function ProjectTicketsPage() {
  const { project, members, memberName } = useProjectContext()
  const { user } = useAuth()
  const currentUserId = user?.id ?? ''
  const { state, retry, reload } = useProjectTickets(project.id)
  const [searchParams, setSearchParams] = useSearchParams()
  const [creatingFrom, setCreatingFrom] = useState<HTMLElement | null | undefined>(undefined)
  const [announcement, setAnnouncement] = useState('')
  const newTicketId = useId()
  usePageTitle(project.name)

  // Creating the first ticket replaces the empty state (and its button)
  // with the list: once that reload lands, focus moves to the header's
  // "New ticket" button instead of being lost.
  const focusNewTicketAfterReload = useRef(false)
  useEffect(() => {
    if (!focusNewTicketAfterReload.current || state.status !== 'ready' || state.refreshing) {
      return
    }
    focusNewTicketAfterReload.current = false
    if (!document.activeElement || document.activeElement === document.body) {
      document.getElementById(newTicketId)?.focus()
    }
  }, [state, newTicketId])

  function openCreate(event: MouseEvent<HTMLButtonElement>) {
    setCreatingFrom(event.currentTarget)
  }

  const tickets = useMemo(() => (state.status === 'ready' ? state.data : []), [state])
  const labelOptions = useMemo(() => labelsInTickets(tickets), [tickets])
  const filters = useMemo(
    () =>
      readTicketFilters(searchParams, {
        memberIds: new Set(members.map((member) => member.id)),
        labelIds: new Set(labelOptions.map((label) => label.id)),
      }),
    [searchParams, members, labelOptions],
  )
  const filtering = hasActiveFilters(filters)
  const shown = useMemo(() => filterTickets(tickets, filters, currentUserId), [tickets, filters, currentUserId])

  // The result count, for screen readers, once the filters have settled:
  // announcing on every keystroke of a search would queue one message per
  // character.
  const resultSummary = filtering ? `${shown.length} of ${tickets.length} tickets shown.` : ''
  const [announcedSummary, setAnnouncedSummary] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => setAnnouncedSummary(resultSummary), 600)
    return () => clearTimeout(timer)
  }, [resultSummary])

  // Typing replaces the current history entry; choosing a filter adds one, so Back undoes it.
  function search(query: string) {
    setSearchParams((current) => withFilter(current, 'q', query), { replace: true })
  }
  function filter(name: Exclude<FilterParam, 'q'>, value: string | null) {
    setSearchParams((current) => withFilter(current, name, value))
  }
  function clearFilters() {
    setSearchParams((current) => withoutFilters(current))
  }

  function handleCreated(ticket: Ticket) {
    setCreatingFrom(undefined)
    setAnnouncement(`${ticket.displayKey} created.`)
    focusNewTicketAfterReload.current = true
    reload()
  }

  let content
  switch (state.status) {
    case 'idle':
    case 'loading':
      content = <TicketsSkeleton />
      break
    case 'error':
      content = <LoadError message="The tickets could not be loaded." reason={state.reason} onRetry={retry} />
      break
    case 'ready':
      content =
        state.data.length === 0 ? (
          <TicketsEmptyState projectKey={project.key} onCreate={openCreate} />
        ) : (
          <div className="flex flex-col gap-3">
            {state.refreshFailed && (
              <p className="text-sm text-ink-muted">
                The list could not be refreshed and may be out of date.{' '}
                <button
                  type="button"
                  onClick={reload}
                  className="font-medium text-accent underline-offset-4 hover:underline"
                >
                  Refresh
                </button>
              </p>
            )}
            <TicketFilterBar
              filters={filters}
              members={members}
              currentUserId={currentUserId}
              labels={labelOptions}
              onSearch={search}
              onFilter={filter}
              onClear={filtering && shown.length > 0 ? clearFilters : undefined}
            />
            {shown.length > 0 ? (
              <ProjectTicketList tickets={shown} memberName={memberName} now={state.receivedAt} />
            ) : (
              <section
                aria-labelledby="tickets-no-match"
                className="rounded-md border border-line bg-surface px-5 py-4"
              >
                <h2 id="tickets-no-match" className="text-sm font-semibold text-ink">
                  No tickets match these filters.
                </h2>
                <div className="mt-3">
                  <Button variant="secondary" size="sm" onClick={clearFilters}>
                    Clear filters
                  </Button>
                </div>
              </section>
            )}
          </div>
        )
      break
  }

  const ticketCount = state.status === 'ready' ? state.data.length : null
  const hasTickets = ticketCount !== null && ticketCount > 0
  return (
    <div className="flex max-w-6xl flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <div className="min-w-0">
          <p className="text-sm text-ink-muted">
            <span className="font-mono text-xs text-ink">{project.key}</span>
            {ticketCount !== null && (
              <>
                {' · '}
                <span>
                  {filtering && ticketCount > 0 ? `${shown.length} of ` : ''}
                  {ticketCount} {ticketCount === 1 ? 'ticket' : 'tickets'}
                </span>
              </>
            )}
          </p>
          {project.description && (
            <p className="mt-1 max-w-3xl text-sm leading-6 break-words text-ink-muted">{project.description}</p>
          )}
        </div>
        {hasTickets && (
          <Button id={newTicketId} onClick={openCreate}>
            New ticket
          </Button>
        )}
      </div>
      {content}
      <p role="status" className="sr-only">
        {announcedSummary}
      </p>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {creatingFrom !== undefined && (
        <CreateTicketDialog
          onClose={() => setCreatingFrom(undefined)}
          onCreated={handleCreated}
          returnFocus={creatingFrom}
          fallbackFocus={() => document.getElementById(newTicketId)}
        />
      )}
    </div>
  )
}

function TicketsEmptyState({
  projectKey,
  onCreate,
}: {
  projectKey: string
  onCreate: (event: MouseEvent<HTMLButtonElement>) => void
}) {
  return (
    <section aria-labelledby="tickets-empty" className="max-w-lg rounded-md border border-line bg-surface px-5 py-4">
      <h2 id="tickets-empty" className="text-sm font-semibold text-ink">
        No tickets yet
      </h2>
      <p className="mt-1.5 text-sm leading-6 text-ink-muted">
        Tickets track the work in this project. Each one gets a key like{' '}
        <span className="font-mono text-xs text-ink">{projectKey}-1</span>.
      </p>
      <div className="mt-3 border-t border-line pt-3">
        <Button onClick={onCreate}>New ticket</Button>
      </div>
    </section>
  )
}

function TicketsSkeleton() {
  return (
    <div aria-busy="true">
      <span className="sr-only" role="status">
        Loading tickets…
      </span>
      <div aria-hidden="true" className="divide-y divide-line rounded-md border border-line bg-surface">
        {[62, 48, 55, 40].map((width) => (
          <div key={width} className="flex items-center gap-3 px-4 py-3">
            <div className={`h-3 w-16 shrink-0 ${BLOCK}`} />
            <div className={`h-3 ${BLOCK}`} style={{ width: `${width}%` }} />
          </div>
        ))}
      </div>
    </div>
  )
}
