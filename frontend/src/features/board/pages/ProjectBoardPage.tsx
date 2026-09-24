import { X } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type MouseEvent } from 'react'
import { useSearchParams } from 'react-router'

import type { Ticket, TicketStatus } from '../../../api/tickets'
import { Button } from '../../../components/ui/Button'
import { LoadError } from '../../../components/ui/LoadError'
import { usePageTitle } from '../../../hooks/usePageTitle'
import { useAuth } from '../../auth/useAuth'
import { ProjectViewNav } from '../../projects/components/ProjectViewNav'
import { useProjectContext } from '../../projects/projectContext'
import { CreateTicketDialog } from '../../tickets/components/CreateTicketDialog'
import { TicketFilterBar } from '../../tickets/components/TicketFilterBar'
import {
  filterTickets,
  hasActiveFilters,
  labelsInTickets,
  readTicketFilters,
  withFilter,
  withoutFilters,
  type FilterParam,
} from '../../tickets/ticketFilters'
import { STATUS_LABELS } from '../../tickets/ticketDisplay'
import { BoardColumns } from '../components/BoardColumns'
import { BoardDragDrop, type DropMethod } from '../components/BoardDragDrop'
import { BoardSkeleton } from '../components/BoardSkeleton'
import { BoardTicketCard } from '../components/BoardTicketCard'
import { MoveTicketMenu } from '../components/MoveTicketMenu'
import { useBoardTickets } from '../useBoardTickets'

/**
 * /app/projects/:projectKey/board: the project's tickets - the same ones
 * as its list - in one column per status. A ticket is moved by dragging it
 * to another column, or with its "Move" control; both take the same path:
 * the card changes column at once and the new status is saved (PATCH,
 * status only); if saving fails it goes back and a message above the
 * board says why. Search and the priority, assignee and label filters
 * are the list's own, in the address, and narrow the board without
 * requests; a status in the address is ignored here (the columns are the
 * statuses). Keyed by project, so nothing of one project's board - moves
 * included - carries over to another's.
 */
export function ProjectBoardPage() {
  const { project } = useProjectContext()
  return <ProjectBoard key={project.id} />
}

function ProjectBoard() {
  const { project, members, memberName } = useProjectContext()
  const { user } = useAuth()
  const currentUserId = user?.id ?? ''
  const { state, tickets, retry, reload, savingIds, move, moveError, dismissMoveError } = useBoardTickets(project.id)
  const [searchParams, setSearchParams] = useSearchParams()
  const [creatingFrom, setCreatingFrom] = useState<HTMLElement | null | undefined>(undefined)
  const [announcement, setAnnouncement] = useState('')
  const newTicketId = useId()
  usePageTitle(project.name, `${project.key} Board`)

  // Creating the first ticket replaces the empty state (and its button)
  // with the board: once that reload lands, focus moves to the header's
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

  // A moved card is a new element in another column (and again if the move
  // is undone), so the focus it had is lost: it goes back to the control
  // the move was made with - the card's Move button, or its drag handle
  // after a keyboard drag - unless the user has already put it somewhere
  // else. Mouse and touch drags leave focus alone.
  const boardRef = useRef<HTMLDivElement>(null)
  const moveErrorRef = useRef<HTMLDivElement>(null)
  /** Which control of which ticket gets focus - or, for `error`, the move's error message. */
  const [focusRequest, setFocusRequest] = useState<{ ticketId: string; control: FocusControl; n: number } | null>(
    null,
  )
  const handledFocusRequest = useRef(0)
  useEffect(() => {
    if (!focusRequest || handledFocusRequest.current === focusRequest.n) {
      return
    }
    handledFocusRequest.current = focusRequest.n
    const active = document.activeElement
    if (active && active !== document.body) {
      return
    }
    const id = CSS.escape(focusRequest.ticketId)
    const target =
      focusRequest.control === 'error'
        ? moveErrorRef.current
        : boardRef.current?.querySelector<HTMLElement>(
            focusRequest.control === 'handle' ? `[data-drag-handle="${id}"]` : `[data-move-trigger="${id}"]`,
          )
    target?.focus()
  }, [focusRequest, tickets])

  function followFocus(ticketId: string, control: FocusControl) {
    setFocusRequest((current) => ({ ticketId, control, n: (current?.n ?? 0) + 1 }))
  }

  /**
   * The one way a ticket changes column, for the Move control and for a
   * drop alike. `focus` is the control that follows the card, if any.
   */
  async function handleMove(ticket: Ticket, status: TicketStatus, focus: FocusControl | null) {
    if (focus) {
      followFocus(ticket.id, focus)
    }
    const outcome = await move(ticket.id, status)
    if (outcome === 'moved') {
      setAnnouncement(`${ticket.displayKey} moved to ${STATUS_LABELS[status]}.`)
    } else if (outcome === 'failed' && focus) {
      followFocus(ticket.id, focus)
    } else if (outcome === 'gone') {
      // Its card is about to disappear: the message saying so takes focus.
      followFocus(ticket.id, 'error')
    }
  }

  function handleDrop(ticketId: string, status: TicketStatus, method: DropMethod) {
    const ticket = tickets.find((candidate) => candidate.id === ticketId)
    if (ticket) {
      void handleMove(ticket, status, method === 'keyboard' ? 'handle' : null)
    }
  }

  // The list's filters, but never status: a status in the address is ignored.
  const labelOptions = useMemo(() => labelsInTickets(tickets), [tickets])
  const filters = useMemo(
    () => ({
      ...readTicketFilters(searchParams, {
        memberIds: new Set(members.map((member) => member.id)),
        labelIds: new Set(labelOptions.map((label) => label.id)),
      }),
      status: null,
    }),
    [searchParams, members, labelOptions],
  )
  const filtering = hasActiveFilters(filters)
  const shown = useMemo(() => filterTickets(tickets, filters, currentUserId), [tickets, filters, currentUserId])

  // The result count, for screen readers, once the filters have settled (as on the list).
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

  function openCreate(event: MouseEvent<HTMLButtonElement>) {
    setCreatingFrom(event.currentTarget)
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
      content = <BoardSkeleton />
      break
    case 'error':
      content = <LoadError message="The tickets could not be loaded." reason={state.reason} onRetry={retry} />
      break
    case 'ready':
      content =
        state.data.length === 0 ? (
          <BoardEmptyState projectKey={project.key} onCreate={openCreate} />
        ) : (
          <div className="flex flex-col gap-3">
            {state.refreshFailed && (
              <p className="text-sm text-ink-muted">
                The board could not be refreshed and may be out of date.{' '}
                <button
                  type="button"
                  onClick={reload}
                  className="font-medium text-accent underline-offset-4 hover:underline"
                >
                  Refresh
                </button>
              </p>
            )}
            {moveError && (
              <div
                ref={moveErrorRef}
                role="alert"
                tabIndex={-1}
                className="flex max-w-3xl items-start gap-3 rounded-md border border-danger/25 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger"
              >
                <p className="min-w-0 flex-1 break-words">{moveError.message}</p>
                <button
                  type="button"
                  onClick={() => {
                    // The button goes away with the message: focus moves to
                    // the ticket's Move button (if it is still on the board).
                    followFocus(moveError.ticketId, 'move')
                    dismissMoveError()
                  }}
                  aria-label="Dismiss"
                  className="-my-1 -mr-1 inline-flex size-7 shrink-0 items-center justify-center rounded text-danger hover:bg-danger/10"
                >
                  <X aria-hidden="true" className="size-4" strokeWidth={2} />
                </button>
              </div>
            )}
            <TicketFilterBar
              filters={filters}
              members={members}
              currentUserId={currentUserId}
              labels={labelOptions}
              onSearch={search}
              onFilter={filter}
              onClear={filtering ? clearFilters : undefined}
              hideStatus
            />
            {filtering && shown.length === 0 && (
              <section aria-labelledby="board-no-match" className="rounded-md border border-line bg-surface px-5 py-4">
                <h2 id="board-no-match" className="text-sm font-semibold text-ink">
                  No tickets match these filters.
                </h2>
                <div className="mt-3">
                  <Button variant="secondary" size="sm" onClick={clearFilters}>
                    Clear filters
                  </Button>
                </div>
              </section>
            )}
            <div ref={boardRef}>
              {/* Any change of the filters ends a drag in progress, as cancelled. */}
              <BoardDragDrop onDrop={handleDrop} resetKey={searchParams.toString()}>
                <BoardColumns
                  tickets={shown}
                  allTickets={filtering ? tickets : undefined}
                  renderTicket={(ticket) => (
                    <BoardTicketCard
                      ticket={ticket}
                      memberName={memberName}
                      saving={savingIds.has(ticket.id)}
                      footer={
                        <MoveTicketMenu
                          ticket={ticket}
                          saving={savingIds.has(ticket.id)}
                          onMove={(status) => void handleMove(ticket, status, 'move')}
                        />
                      }
                    />
                  )}
                />
              </BoardDragDrop>
            </div>
          </div>
        )
      break
  }

  const ticketCount = state.status === 'ready' ? state.data.length : null
  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <div className="min-w-0">
          <p className="text-sm text-ink-muted">
            <span className="font-mono text-xs text-ink">{project.key}</span>
            {ticketCount !== null && (
              <>
                {' · '}
                {filtering && ticketCount > 0 ? `${shown.length} of ` : ''}
                {ticketCount} {ticketCount === 1 ? 'ticket' : 'tickets'}
              </>
            )}
          </p>
          {project.description && (
            <p className="mt-1 max-w-3xl text-sm leading-6 break-words text-ink-muted">{project.description}</p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ProjectViewNav projectKey={project.key} filters={filters} />
          {ticketCount !== null && ticketCount > 0 && (
            <Button id={newTicketId} onClick={openCreate}>
              New ticket
            </Button>
          )}
        </div>
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

type FocusControl = 'move' | 'handle' | 'error'

function BoardEmptyState({
  projectKey,
  onCreate,
}: {
  projectKey: string
  onCreate: (event: MouseEvent<HTMLButtonElement>) => void
}) {
  return (
    <section aria-labelledby="board-empty" className="max-w-lg rounded-md border border-line bg-surface px-5 py-4">
      <h2 id="board-empty" className="text-sm font-semibold text-ink">
        No tickets yet
      </h2>
      <p className="mt-1.5 text-sm leading-6 text-ink-muted">
        The board shows this project's tickets in a column for each status. Each ticket gets a key like{' '}
        <span className="font-mono text-xs text-ink">{projectKey}-1</span>.
      </p>
      <div className="mt-3 border-t border-line pt-3">
        <Button onClick={onCreate}>New ticket</Button>
      </div>
    </section>
  )
}
