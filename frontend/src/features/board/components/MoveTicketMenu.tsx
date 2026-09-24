import { ChevronDown } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'

import type { Ticket, TicketStatus } from '../../../api/tickets'
import { STATUS_LABELS } from '../../tickets/ticketDisplay'
import { STATUS_ORDER } from '../boardColumns'

interface MoveTicketMenuProps {
  ticket: Ticket
  /** The ticket's move is being saved: the control stays focusable but does nothing. */
  saving: boolean
  onMove: (status: TicketStatus) => void
}

/**
 * "Move": a button that shows the ticket's other four statuses, each a
 * button that moves it there - the way to move a ticket without dragging.
 * A disclosure rather than a select: arrow keys on a closed select would
 * move the ticket once per key press. The destinations open below the
 * button, inside the card (a popup would be clipped by the board's
 * scroller); arrow keys move between them. Escape or the button closes
 * them and focus returns to the button; clicking elsewhere closes them too.
 * While the move is saved the button stays focusable (focus follows the
 * card to its new column) but does nothing, and says "Saving…".
 *
 * Every trigger carries data-move-trigger (the ticket's id), so the board
 * can put focus back on it once the card has moved to another column.
 */
export function MoveTicketMenu({ ticket, saving, onMove }: MoveTicketMenuProps) {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const panelId = useId()
  const savingId = useId()
  const current = STATUS_LABELS[ticket.status]
  const destinations = STATUS_ORDER.filter((status) => status !== ticket.status)

  // Opening moves focus to the first destination and brings all of them
  // into view (a card low on the screen would otherwise open them below
  // it); a click outside closes.
  useEffect(() => {
    if (!open) {
      return
    }
    containerRef.current?.querySelector<HTMLElement>('[data-destination]')?.focus({ preventScroll: true })
    document.getElementById(panelId)?.scrollIntoView({ block: 'nearest' })
    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open, panelId])

  function close() {
    setOpen(false)
    triggerRef.current?.focus()
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (!open) {
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      close()
      return
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      const buttons = [...(containerRef.current?.querySelectorAll<HTMLElement>('[data-destination]') ?? [])]
      const index = buttons.indexOf(document.activeElement as HTMLElement)
      if (index !== -1) {
        event.preventDefault()
        const step = event.key === 'ArrowDown' ? 1 : -1
        buttons[(index + step + buttons.length) % buttons.length]?.focus()
      }
    }
  }

  function choose(status: TicketStatus) {
    setOpen(false)
    onMove(status)
  }

  return (
    <div ref={containerRef} onKeyDown={handleKeyDown} className="flex flex-col gap-2">
      <div className="flex min-h-8 items-center justify-end gap-2 max-sm:min-h-11">
        {saving && (
          <span id={savingId} className="mr-auto text-xs text-ink-muted">
            Saving…
          </span>
        )}
        <button
          ref={triggerRef}
          type="button"
          data-move-trigger={ticket.id}
          aria-label={`Move ${ticket.displayKey}, currently ${current}`}
          aria-expanded={open}
          aria-controls={open ? panelId : undefined}
          aria-disabled={saving || undefined}
          aria-describedby={saving ? savingId : undefined}
          onClick={() => {
            if (!saving) {
              setOpen((isOpen) => !isOpen)
            }
          }}
          className={`inline-flex h-8 shrink-0 items-center gap-1 rounded-md border border-line px-2.5 max-sm:h-11 max-sm:px-3.5 text-xs font-medium text-ink-muted hover:bg-canvas hover:text-ink aria-disabled:cursor-not-allowed aria-disabled:opacity-60 aria-disabled:hover:bg-surface ${open ? 'bg-canvas text-ink' : 'bg-surface'}`}
        >
          Move
          <ChevronDown aria-hidden="true" className={`size-3.5 ${open ? 'rotate-180' : ''}`} strokeWidth={2} />
        </button>
      </div>
      {open && (
        <div id={panelId} role="group" aria-label={`Move ${ticket.displayKey} to`}>
          <p aria-hidden="true" className="mb-1.5 text-xs font-medium text-ink-subtle">
            Move to…
          </p>
          <ul className="grid grid-cols-2 gap-1.5">
            {destinations.map((status) => (
              <li key={status}>
                <button
                  type="button"
                  data-destination
                  onClick={() => choose(status)}
                  className="inline-flex h-8 w-full items-center rounded-md border border-line bg-surface px-2 max-sm:h-11 text-left text-xs font-medium text-ink hover:border-accent/40 hover:bg-accent-subtle hover:text-accent"
                >
                  {STATUS_LABELS[status]}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
