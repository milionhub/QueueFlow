import { ListTodo, Plus, SearchX } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type MouseEvent } from 'react'
import { useSearchParams } from 'react-router'

import type { Ticket } from '../../../api/tickets'
import { Button } from '../../../components/ui/Button'
import { LoadError } from '../../../components/ui/LoadError'
import { EmptyState, SkeletonFrame, StaleNotice } from '../../../components/ui/States'
import { useToast } from '../../../components/ui/toastContext'
import { usePageTitle } from '../../../hooks/usePageTitle'
import { ticketPath } from '../../../routes/paths'
import { useAuth } from '../../auth/useAuth'
import { ProjectPageHeader } from '../../projects/components/ProjectPageHeader'
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

/**
 * /app/projects/:projectKey: the project's page is its ticket list. The
 * header shows the project's key, name and description. Anyone in the
 * workspace can create tickets; after a create the list reloads, so the
 * order stays the backend's, and a toast links to the new ticket. Search
 * and filters live in the address and narrow the loaded list without
 * requests.
 */
export function ProjectTicketsPage() {
  const { project, members, memberName } = useProjectContext()
  const { user } = useAuth()
  const toast = useToast()
  const currentUserId = user?.id ?? ''
  const { state, retry, reload } = useProjectTickets(project.id)
  const [searchParams, setSearchParams] = useSearchParams()
  const [creatingFrom, setCreatingFrom] = useState<HTMLElement | null | undefined>(undefined)
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
    toast.show({
      message: `${ticket.displayKey} created.`,
      action: { label: 'Open', to: ticketPath(ticket.projectKey, ticket.ticketNumber) },
    })
    focusNewTicketAfterReload.current = true
    reload()
  }

  const ticketCount = state.status === 'ready' ? state.data.length : null
  const hasTickets = ticketCount !== null && ticketCount > 0

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
          <EmptyState
            icon={ListTodo}
            title="No tickets yet"
            action={
              <Button onClick={openCreate}>
                <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
                New ticket
              </Button>
            }
            className="max-w-2xl"
          >
            Tickets track the work in this project. Each one gets a key like{' '}
            <span className="font-mono text-xs text-ink">{project.key}-1</span>.
          </EmptyState>
        ) : (
          <div className="flex flex-col gap-3">
            {state.refreshFailed && (
              <StaleNotice onRefresh={reload}>The list could not be refreshed and may be out of date.</StaleNotice>
            )}
            <TicketFilterBar
              filters={filters}
              members={members}
              currentUserId={currentUserId}
              labels={labelOptions}
              onSearch={search}
              onFilter={filter}
              onClear={filtering && shown.length > 0 ? clearFilters : undefined}
              summary={
                filtering
                  ? `${shown.length} of ${state.data.length} tickets`
                  : `${state.data.length} ${state.data.length === 1 ? 'ticket' : 'tickets'}`
              }
            />
            {shown.length > 0 ? (
              <ProjectTicketList tickets={shown} memberName={memberName} now={state.receivedAt} />
            ) : (
              <EmptyState
                icon={SearchX}
                title="No tickets match these filters"
                action={
                  <Button variant="secondary" onClick={clearFilters}>
                    Clear filters
                  </Button>
                }
              >
                Try a different search, or clear the filters to see all {state.data.length}{' '}
                {state.data.length === 1 ? 'ticket' : 'tickets'}.
              </EmptyState>
            )}
          </div>
        )
      break
  }

  return (
    <div className="flex max-w-7xl flex-col gap-5">
      <ProjectPageHeader
        project={project}
        filters={filters}
        action={
          hasTickets && (
            <Button id={newTicketId} onClick={openCreate}>
              <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
              New ticket
            </Button>
          )
        }
      />
      {content}
      <p role="status" className="sr-only">
        {announcedSummary}
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

function TicketsSkeleton() {
  return (
    <SkeletonFrame label="Loading tickets…">
      <div className="flex flex-col gap-3">
        <div className="skeleton h-9 w-full max-w-80" />
        <div className="divide-y divide-line rounded-lg border border-line bg-surface shadow-xs">
          {[62, 48, 55, 40].map((width) => (
            <div key={width} className="flex h-11 items-center gap-3 px-4">
              <div className="skeleton h-3 w-16 shrink-0" />
              <div className="skeleton h-3" style={{ width: `${width}%` }} />
            </div>
          ))}
        </div>
      </div>
    </SkeletonFrame>
  )
}
