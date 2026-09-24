import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { updateTicket, type Ticket, type TicketStatus } from '../../api/tickets'
import type { ResourceState } from '../../lib/useResource'
import { useAuth } from '../auth/useAuth'
import { isTicketGone, ticketChangeError } from '../tickets/ticketErrors'
import { STATUS_LABELS } from '../tickets/ticketDisplay'
import { useProjectTickets } from '../tickets/useProjectTickets'

/** A move the backend has not answered yet: the card already shows `status`. */
interface PendingMove {
  status: TicketStatus
  requestId: number
}

export interface MoveError {
  ticketId: string
  message: string
}

/**
 * How a move ended: `moved`; `failed` (the card is back where it was);
 * `gone` (the ticket no longer exists - the list is reloaded, which removes
 * its card); or `ignored` - no request was made, because the ticket is
 * already in that status, another move of it is still being saved, or it
 * is not on the board.
 */
export type MoveOutcome = 'moved' | 'failed' | 'gone' | 'ignored'

export interface BoardTickets {
  /** The project's ticket list as loaded (for loading, errors and refreshes). */
  state: ResourceState<Ticket[]>
  /** The tickets to draw: the list, with confirmed changes and pending moves applied. */
  tickets: Ticket[]
  retry: () => void
  reload: () => void
  /** The tickets whose move is being saved. */
  savingIds: ReadonlySet<string>
  /** Moves a ticket to another status at once, then saves it; undone if the save fails. */
  move: (ticketId: string, status: TicketStatus) => Promise<MoveOutcome>
  /** The last failed move, until dismissed or that ticket is moved again. */
  moveError: MoveError | null
  dismissMoveError: () => void
}

/**
 * A ticket's `updatedAt` in microseconds. The backend sends up to six
 * fractional digits ("…:12.123456Z"), more than Date keeps, and two saves
 * of one ticket can fall in the same millisecond.
 */
function updatedAtMicros(ticket: Ticket): number {
  const fraction = /\.(\d+)/.exec(ticket.updatedAt)?.[1] ?? ''
  const seconds = Date.parse(ticket.updatedAt.replace(/\.\d+/, ''))
  return seconds * 1000 + Number(fraction.padEnd(6, '0').slice(0, 6))
}

/**
 * The board's tickets and their moves. What is drawn is derived, never a
 * second copy of the list:
 *
 * 1. the project's ticket list, as last loaded;
 * 2. each ticket a move saved: the backend's answer, kept while it is newer
 *    (by `updatedAt`) than the list's copy - so a list request that was
 *    already under way when the move was saved cannot undo it - and dropped
 *    once a newer list has caught up;
 * 3. each move still being saved: the ticket shown in its new status.
 *
 * One move per ticket at a time; different tickets move independently.
 * A move whose answer arrives after its board is gone (another project,
 * or the page left) changes nothing.
 */
export function useBoardTickets(projectId: string): BoardTickets {
  const { authorizedRequest } = useAuth()
  const { state, retry, reload } = useProjectTickets(projectId)
  const [confirmed, setConfirmed] = useState<ReadonlyMap<string, Ticket>>(new Map())
  const [pending, setPending] = useState<ReadonlyMap<string, PendingMove>>(new Map())
  const [moveError, setMoveError] = useState<MoveError | null>(null)

  // Read synchronously by move(): two quick moves of one ticket in the same
  // render must not both start.
  const pendingRef = useRef(new Map<string, PendingMove>())
  const nextRequestId = useRef(0)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const listed = state.status === 'ready' ? state.data : null

  // A saved answer the list has caught up with is no longer needed: when a
  // new list arrives, those are dropped (during render, React's pattern for
  // state that follows a changing value - no extra effect pass).
  const [prunedFor, setPrunedFor] = useState(listed)
  if (listed !== prunedFor) {
    setPrunedFor(listed)
    if (listed && confirmed.size > 0) {
      const byId = new Map(listed.map((ticket) => [ticket.id, ticket]))
      const kept = new Map(confirmed)
      for (const [id, saved] of confirmed) {
        const inList = byId.get(id)
        if (!inList || updatedAtMicros(inList) >= updatedAtMicros(saved)) {
          kept.delete(id)
        }
      }
      if (kept.size !== confirmed.size) {
        setConfirmed(kept)
      }
    }
  }

  const tickets = useMemo(() => {
    if (!listed) {
      return []
    }
    return listed.map((inList) => {
      const saved = confirmed.get(inList.id)
      const ticket = saved && updatedAtMicros(saved) > updatedAtMicros(inList) ? saved : inList
      const move = pending.get(ticket.id)
      return move && move.status !== ticket.status ? { ...ticket, status: move.status } : ticket
    })
  }, [listed, confirmed, pending])

  // move() reads the latest tickets without being recreated on every change.
  const latestTickets = useRef(tickets)
  useEffect(() => {
    latestTickets.current = tickets
  })

  const settle = useCallback((ticketId: string, requestId: number) => {
    const current = pendingRef.current.get(ticketId)
    if (current?.requestId !== requestId) {
      return false
    }
    pendingRef.current.delete(ticketId)
    setPending(new Map(pendingRef.current))
    return true
  }, [])

  const move = useCallback(
    async (ticketId: string, status: TicketStatus): Promise<MoveOutcome> => {
      const ticket = latestTickets.current.find((candidate) => candidate.id === ticketId)
      if (!ticket || ticket.status === status || pendingRef.current.has(ticketId)) {
        return 'ignored'
      }
      const requestId = ++nextRequestId.current
      pendingRef.current.set(ticketId, { status, requestId })
      setPending(new Map(pendingRef.current))
      setMoveError((current) => (current?.ticketId === ticketId ? null : current))

      try {
        const saved = await updateTicket(authorizedRequest, ticketId, { status })
        if (!mounted.current || !settle(ticketId, requestId)) {
          return 'ignored'
        }
        setConfirmed((current) => {
          const previous = current.get(ticketId)
          if (previous && updatedAtMicros(previous) > updatedAtMicros(saved)) {
            return current
          }
          return new Map(current).set(ticketId, saved)
        })
        return 'moved'
      } catch (error) {
        if (!mounted.current || !settle(ticketId, requestId)) {
          return 'ignored'
        }
        if (isTicketGone(error)) {
          // Deleted, or no longer visible: the list is reloaded so the card goes.
          setMoveError({ ticketId, message: `${ticket.displayKey} is no longer available.` })
          reload()
          return 'gone'
        } else {
          setMoveError({
            ticketId,
            message: `Couldn't move ${ticket.displayKey} to ${STATUS_LABELS[status]}: ${ticketChangeError(error).message}`,
          })
        }
        return 'failed'
      }
    },
    [authorizedRequest, reload, settle],
  )

  const savingIds = useMemo(() => new Set(pending.keys()), [pending])
  const dismissMoveError = useCallback(() => setMoveError(null), [])

  return { state, tickets, retry, reload, savingIds, move, moveError, dismissMoveError }
}
