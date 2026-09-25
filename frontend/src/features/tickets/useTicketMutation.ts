import { useCallback, useEffect, useRef, useState } from 'react'

import type { Ticket } from '../../api/tickets'
import { isTicketGone } from './ticketErrors'

export type MutationResult = { ok: true; ticket: Ticket } | { ok: false; error: unknown }

export interface TicketMutation {
  /** What is being saved right now ("status", "title", "label:<id>", ...), or null. */
  pending: string | null
  /**
   * Runs one change of the ticket. Only one runs at a time: while one is
   * under way `pending` is set and every control waits, so an older
   * response can never overwrite a newer one. The ticket the backend
   * returns replaces the page's copy. Resolves to null if another change
   * was already running.
   */
  run: (part: string, request: () => Promise<Ticket>) => Promise<MutationResult | null>
}

/**
 * Changes to the ticket on the detail page. `replace` swaps in the ticket
 * the backend returned - only if it is still the ticket shown. `onTicketGone`
 * is called when the backend answers that the ticket itself no longer
 * exists (404), so the page can show "not found". `onChanged`, if given,
 * is called after each successful change (e.g. to reload the history).
 */
export function useTicketMutation(
  ticketId: string,
  replace: (ticket: Ticket) => void,
  onTicketGone: () => void,
  onChanged?: () => void,
): TicketMutation {
  const [pending, setPending] = useState<string | null>(null)
  const running = useRef(false)
  const latest = useRef({ ticketId, replace, onTicketGone, onChanged })
  useEffect(() => {
    latest.current = { ticketId, replace, onTicketGone, onChanged }
  })

  const run = useCallback(async (part: string, request: () => Promise<Ticket>): Promise<MutationResult | null> => {
    if (running.current) {
      return null
    }
    running.current = true
    setPending(part)
    try {
      const ticket = await request()
      if (ticket.id === latest.current.ticketId) {
        latest.current.replace(ticket)
        latest.current.onChanged?.()
      }
      return { ok: true, ticket }
    } catch (error) {
      if (isTicketGone(error)) {
        latest.current.onTicketGone()
      }
      return { ok: false, error }
    } finally {
      running.current = false
      setPending(null)
    }
  }, [])

  return { pending, run }
}
