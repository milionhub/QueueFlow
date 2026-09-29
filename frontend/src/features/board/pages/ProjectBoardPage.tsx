import { Columns3, Plus, X } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type MouseEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'

import type { Ticket, TicketStatus } from '../../../api/tickets'
import { Button } from '../../../components/ui/Button'
import { IconButton } from '../../../components/ui/IconButton'
import { LoadError } from '../../../components/ui/LoadError'
import { EmptyState, StaleNotice } from '../../../components/ui/States'
import { useToast } from '../../../components/ui/toastContext'
import { usePageTitle } from '../../../hooks/usePageTitle'
import { ticketPath } from '../../../routes/paths'
import { useAuth } from '../../auth/useAuth'
import { ProjectPageHeader } from '../../projects/components/ProjectPageHeader'
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
import { BoardColumns } from '../components/BoardColumns'
import { BoardDragDrop, type DropMethod } from '../components/BoardDragDrop'
import { BoardSkeleton } from '../components/BoardSkeleton'
import { BoardTicketCard } from '../components/BoardTicketCard'
import { MoveTicketMenu } from '../components/MoveTicketMenu'
import { useBoardTickets, type MoveError } from '../useBoardTickets'

/**
 * /app/projects/:projectKey/board: the project's tickets - the same ones
 * as its list - in one column per status. A ticket is moved by dragging it
 * to another column, or with its "Move" control; both take the same path:
 * the card changes column at once and the new status is saved (PATCH,
 * status only); if saving fails it goes back and a toast says why, with
 * Retry (a ticket that no longer exists is reported above the board). Search and the priority, assignee and label filters
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
  const { t } = useTranslation(['board', 'tickets', 'common', 'shell'])
  const { project, members, memberName } = useProjectContext()
  const { user } = useAuth()
  const currentUserId = user?.id ?? ''
  const { state, tickets, retry, reload, savingIds, move, moveError, dismissMoveError } = useBoardTickets(project.id)
  const [searchParams, setSearchParams] = useSearchParams()
  const [creatingFrom, setCreatingFrom] = useState<HTMLElement | null | undefined>(undefined)
  const [announcement, setAnnouncement] = useState('')
  const toast = useToast()
  const newTicketId = useId()
  usePageTitle(project.name, t('shell:titles.boardDocument', { key: project.key }))

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
  const [focusRequest, setFocusRequest] = useState<{ ticketId: string; control: FocusControl; n: number } | null>(null)
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
      setAnnouncement(t('moved', { key: ticket.displayKey, status: t(`tickets:status.${status}`) }))
    } else if (outcome === 'failed' && focus) {
      followFocus(ticket.id, focus)
    } else if (outcome === 'gone') {
      // Its card is about to disappear: the message saying so takes focus.
      followFocus(ticket.id, 'error')
    }
  }

  // A failed move becomes a toast - visible wherever the board is scrolled -
  // with Retry, which moves the ticket the same way again and brings focus
  // back to its Move button. A ticket that no longer exists stays reported
  // above the board, where focus can be put on the message.
  const reportFailure = useRef<(error: MoveError) => void>(() => {})
  useEffect(() => {
    reportFailure.current = (error) => {
      const ticket = tickets.find((candidate) => candidate.id === error.ticketId)
      toast.show({
        tone: 'error',
        message: error.message,
        action: ticket
          ? {
              label: t('common:actions.retry'),
              onClick: () => {
                // The toast (and its button) goes away: let focus follow the card to its Move button instead.
                ;(document.activeElement as HTMLElement | null)?.blur()
                void handleMove(ticket, error.status, 'move')
              },
            }
          : undefined,
      })
    }
  })
  useEffect(() => {
    if (moveError?.kind === 'failed') {
      reportFailure.current(moveError)
      dismissMoveError()
    }
  }, [moveError, dismissMoveError])

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
  const resultSummary = filtering
    ? t('tickets:list.shownAnnouncement', { shown: shown.length, count: tickets.length })
    : ''
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
    toast.show({
      message: t('tickets:list.created', { key: ticket.displayKey }),
      action: { label: t('common:actions.open'), to: ticketPath(ticket.projectKey, ticket.ticketNumber) },
    })
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
      content = <LoadError message={t('tickets:list.loadError')} reason={state.reason} onRetry={retry} />
      break
    case 'ready':
      content =
        state.data.length === 0 ? (
          <EmptyState
            icon={Columns3}
            title={t('tickets:list.emptyTitle')}
            className="max-w-2xl"
            action={
              <Button onClick={openCreate}>
                <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
                {t('tickets:list.newTicket')}
              </Button>
            }
          >
            <Trans
              t={t}
              i18nKey="emptyBody"
              values={{ key: project.key }}
              components={{ key: <span className="font-mono text-xs text-ink" /> }}
            />
          </EmptyState>
        ) : (
          <div className="flex flex-col gap-3">
            {state.refreshFailed && (
              <StaleNotice onRefresh={reload}>{t('stale')}</StaleNotice>
            )}
            {moveError && (
              <div
                ref={moveErrorRef}
                role="alert"
                tabIndex={-1}
                className="flex max-w-3xl animate-fade-in items-start gap-3 rounded-lg border border-danger/20 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger"
              >
                <p className="min-w-0 flex-1 break-words">{moveError.message}</p>
                <IconButton
                  icon={X}
                  label={t('common:actions.dismiss')}
                  size="sm"
                  onClick={() => {
                    // The button goes away with the message: focus moves to
                    // the ticket's Move button (if it is still on the board).
                    followFocus(moveError.ticketId, 'move')
                    dismissMoveError()
                  }}
                  className="-my-1 -mr-1 text-danger! hover:bg-danger/10!"
                />
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
              summary={
                filtering
                  ? t('tickets:list.filteredCount', { shown: shown.length, count: state.data.length })
                  : t('tickets:list.count', { count: state.data.length })
              }
            />
            {filtering && shown.length === 0 && (
              <p className="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-muted shadow-xs">
                <span className="font-medium text-ink">{t('noMatch')}</span>
                <button
                  type="button"
                  onClick={clearFilters}
                  className="rounded-sm font-medium text-accent underline-offset-4 hover:underline"
                >
                  {t('common:actions.clearFilters')}
                </button>
              </p>
            )}
            <div ref={boardRef} className="flex flex-col gap-2">
              {/* Any change of the filters ends a drag in progress, as cancelled. */}
              <BoardDragDrop onDrop={handleDrop} resetKey={searchParams.toString()}>
                <BoardColumns
                  tickets={shown}
                  allTickets={filtering ? tickets : undefined}
                  renderTicket={(ticket) => (
                    <MoveTicketMenu
                      ticket={ticket}
                      saving={savingIds.has(ticket.id)}
                      onMove={(status) => void handleMove(ticket, status, 'move')}
                    >
                      {({ trigger, saving, panel }) => (
                        <BoardTicketCard
                          ticket={ticket}
                          memberName={memberName}
                          saving={savingIds.has(ticket.id)}
                          headerAction={trigger}
                          note={saving}
                          footer={panel}
                        />
                      )}
                    </MoveTicketMenu>
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
    <div className="flex min-w-0 flex-col gap-5">
      <ProjectPageHeader
        project={project}
        filters={filters}
        action={
          ticketCount !== null &&
          ticketCount > 0 && (
            <Button id={newTicketId} onClick={openCreate}>
              <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
              {t('tickets:list.newTicket')}
            </Button>
          )
        }
      />
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
