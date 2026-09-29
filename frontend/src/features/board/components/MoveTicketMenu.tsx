import { ArrowRightLeft } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import type { Ticket, TicketStatus } from '../../../api/tickets'
import { Spinner } from '../../../components/ui/Spinner'
import { StatusIcon } from '../../tickets/TicketBadges'
import { STATUS_ORDER } from '../boardColumns'

interface MoveTicketMenuProps {
  ticket: Ticket
  /** The ticket's move is being saved: the control stays focusable but does nothing. */
  saving: boolean
  onMove: (status: TicketStatus) => void
  /** Places the parts in the card: the trigger in its header, the destinations at its end. */
  children: (parts: { trigger: ReactNode; saving: ReactNode; panel: ReactNode }) => ReactNode
}

/**
 * "Move": a button that shows the ticket's other four statuses, each a
 * button that moves it there - the way to move a ticket without dragging.
 * A disclosure rather than a select: arrow keys on a closed select would
 * move the ticket once per key press. The destinations open inside the
 * card, below its content (a popup would be clipped by the board's
 * scroller); arrow keys move between them. Escape or the button closes
 * them and focus returns to the button; clicking elsewhere closes them too.
 * While the move is saved the button stays focusable (focus follows the
 * card to its new column) but does nothing, and "Saving…" shows.
 *
 * Every trigger carries data-move-trigger (the ticket's id), so the board
 * can put focus back on it once the card has moved to another column.
 */
export function MoveTicketMenu({ ticket, saving, onMove, children }: MoveTicketMenuProps) {
  const { t } = useTranslation(['board', 'tickets', 'common'])
  const [open, setOpen] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const panelId = useId()
  const savingId = useId()
  const current = t(`tickets:status.${ticket.status}`)
  const destinations = STATUS_ORDER.filter((status) => status !== ticket.status)

  // Opening moves focus to the first destination and brings all of them
  // into view (a card low on the screen would otherwise open them below
  // it); a click outside the trigger and the destinations closes.
  useEffect(() => {
    if (!open) {
      return
    }
    panelRef.current?.querySelector<HTMLElement>('[data-destination]')?.focus({ preventScroll: true })
    panelRef.current?.scrollIntoView({ block: 'nearest' })
    function onPointerDown(event: PointerEvent) {
      const target = event.target as Node
      if (!panelRef.current?.contains(target) && !triggerRef.current?.contains(target)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function close() {
    setOpen(false)
    triggerRef.current?.focus()
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
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
      const buttons = [...(panelRef.current?.querySelectorAll<HTMLElement>('[data-destination]') ?? [])]
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

  const trigger = (
    <button
      ref={triggerRef}
      type="button"
      data-move-trigger={ticket.id}
      aria-label={t('move.trigger', { key: ticket.displayKey, status: current })}
      title={t('move.tooltip')}
      aria-expanded={open}
      aria-controls={open ? panelId : undefined}
      aria-disabled={saving || undefined}
      aria-describedby={saving ? savingId : undefined}
      onKeyDown={handleKeyDown}
      onClick={() => {
        if (!saving) {
          setOpen((isOpen) => !isOpen)
        }
      }}
      className={`press inline-flex size-8 shrink-0 items-center justify-center rounded-md text-ink-subtle transition-colors hover:bg-canvas-strong hover:text-ink aria-disabled:cursor-not-allowed aria-disabled:opacity-40 aria-disabled:hover:bg-transparent max-sm:size-11 ${
        open ? 'bg-canvas-strong text-ink' : ''
      }`}
    >
      <ArrowRightLeft aria-hidden="true" className="size-4" strokeWidth={2} />
    </button>
  )

  const savingNote = saving && (
    <span id={savingId} className="inline-flex items-center gap-1.5 text-xs text-ink-muted">
      <Spinner className="size-3" />
      {t('common:actions.saving')}
    </span>
  )

  const panel = open && (
    <div
      ref={panelRef}
      id={panelId}
      role="group"
      aria-label={t('move.group', { key: ticket.displayKey })}
      onKeyDown={handleKeyDown}
      className="origin-top animate-pop border-t border-line pt-2.5"
    >
      <p aria-hidden="true" className="mb-1.5 text-xs font-medium text-ink-subtle">
        {t('move.heading')}
      </p>
      <ul className="grid grid-cols-2 gap-1.5">
        {destinations.map((status) => (
          <li key={status}>
            <button
              type="button"
              data-destination
              onClick={() => choose(status)}
              className="press inline-flex h-8 w-full items-center gap-1.5 rounded-md border border-line bg-surface px-2 text-left text-xs font-medium text-ink transition-colors hover:border-accent/40 hover:bg-accent-subtle hover:text-accent max-sm:h-11"
            >
              <StatusIcon status={status} className="size-3" />
              <span className="truncate">{t(`tickets:status.${status}`)}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )

  return children({ trigger, saving: savingNote, panel })
}
