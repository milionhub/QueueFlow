import {
  Accessibility,
  AutoScroller,
  Cursor,
  Feedback,
  PointerActivationConstraints,
  PreventSelection,
  type DragDropManager,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
  type Draggable,
  type Droppable,
} from '@dnd-kit/dom'
import { DragDropProvider, DragOverlay, KeyboardSensor, PointerSensor, useDragDropManager } from '@dnd-kit/react'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'

import type { Ticket, TicketStatus } from '../../../api/tickets'
import { STATUS_LABELS } from '../../tickets/ticketDisplay'
import { STATUS_ORDER } from '../boardColumns'
import { BoardCardPreview } from './BoardTicketCard'

/**
 * What the board's draggables and droppables carry: a card its ticket, a
 * column its status. Drag and drop only works out which ticket went to
 * which status; the move itself is the board's one `onDrop` - the same
 * path as the Move control.
 */
export interface CardDragData {
  ticket: Ticket
  [key: string]: unknown
}
export interface ColumnDropData {
  status: TicketStatus
  [key: string]: unknown
}

/** How a drop was made: keyboard drags move focus with the card, pointer drags leave focus alone. */
export type DropMethod = 'keyboard' | 'pointer'

function ticketOf(source: Draggable | null | undefined): Ticket | null {
  return (source?.data as CardDragData | undefined)?.ticket ?? null
}

function statusOf(target: Droppable | null | undefined): TicketStatus | null {
  return (target?.data as ColumnDropData | undefined)?.status ?? null
}

/**
 * Spoken during a drag, with the ticket's key and the columns' names -
 * never ids. They say what the drag is doing; whether the move was saved
 * is announced by the board once the server has answered.
 */
const ACCESSIBILITY = {
  plugin: Accessibility,
  options: {
    screenReaderInstructions: {
      draggable:
        'To move this ticket to another status, press Space or Enter to pick it up, use the Left and Right arrow ' +
        'keys to choose a column, then press Space or Enter to drop it there. Press Escape to cancel.',
    },
    announcements: {
      dragstart({ operation: { source } }: DragStartEvent) {
        const ticket = ticketOf(source)
        return ticket ? `Picked up ${ticket.displayKey}, currently in ${STATUS_LABELS[ticket.status]}.` : undefined
      },
      dragover({ operation: { source, target } }: DragOverEvent) {
        const ticket = ticketOf(source)
        const status = statusOf(target)
        if (!ticket) {
          return undefined
        }
        if (!status) {
          return `${ticket.displayKey} is not over a column.`
        }
        return status === ticket.status
          ? `${ticket.displayKey} is over its current column, ${STATUS_LABELS[status]}.`
          : `${ticket.displayKey} is over ${STATUS_LABELS[status]}.`
      },
      dragend({ operation: { source, target }, canceled }: DragEndEvent) {
        const ticket = ticketOf(source)
        const status = statusOf(target)
        if (!ticket) {
          return undefined
        }
        if (canceled) {
          return `Cancelled. ${ticket.displayKey} stays in ${STATUS_LABELS[ticket.status]}.`
        }
        if (!status) {
          return `Dropped ${ticket.displayKey} outside the columns. It stays in ${STATUS_LABELS[ticket.status]}.`
        }
        if (status === ticket.status) {
          return `Dropped ${ticket.displayKey} in its current column. Nothing changed.`
        }
        return `Dropped ${ticket.displayKey}. Moving to ${STATUS_LABELS[status]}.`
      },
    },
  },
}

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

interface BoardDragDropProps {
  /** Called for a drop on a column other than the ticket's own; never for a cancel. */
  onDrop: (ticketId: string, status: TicketStatus, method: DropMethod) => void
  /** Any change of this value ends a drag in progress, as cancelled (e.g. the filters changed). */
  resetKey: string
  children: ReactNode
}

/**
 * Drag and drop for the board: cards are draggables, the five columns are
 * droppables, and nothing is sortable - a drop anywhere on a column means
 * "this status".
 *
 * - Mouse: drag the card (anywhere but its link and buttons) or its handle,
 *   after 5px of movement, so a click is still a click.
 * - Touch: press and hold the card for 250ms (moving 8px first scrolls
 *   instead), or drag the handle at once.
 * - Keyboard: on the handle, Space or Enter picks the card up, Left and
 *   Right move it one column (scrolling the board to show it), and Space,
 *   Enter or Tab drops it; Escape cancels.
 *
 * The preview is a light copy of the card with no links or buttons. The
 * drop animation is off for reduced motion (and brief otherwise).
 */
export function BoardDragDrop({ onDrop, resetKey, children }: BoardDragDropProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [reducedMotion] = useState(prefersReducedMotion)

  // Sensors and plugins are created once: the provider rebuilds them on every new array.
  const sensors = useMemo(
    () => [
      PointerSensor.configure({
        activatorElements: (source) => [source.element, source.handle],
        activationConstraints(event, source) {
          const onHandle = event.target instanceof Node && source.handle?.contains(event.target)
          if (event.pointerType === 'touch') {
            return onHandle ? undefined : [new PointerActivationConstraints.Delay({ value: 250, tolerance: 8 })]
          }
          return [new PointerActivationConstraints.Distance({ value: 5 })]
        },
      }),
      // The arrow keys do not move the card by pixels: see handleDragStart.
      KeyboardSensor.configure({ offset: { x: 0, y: 0 } }),
    ],
    [],
  )

  // dnd-kit's default plugins, with announcements that name tickets and
  // columns, a brief (or, for reduced motion, no) drop animation, and
  // sideways-only auto-scroll: the board scrolls horizontally, and the
  // page's own scrolling is left alone.
  const plugins = useMemo(
    () => [
      ACCESSIBILITY,
      AutoScroller.configure({ threshold: { x: 0.15, y: 0 } }),
      Cursor,
      Feedback.configure({ dropAnimation: reducedMotion ? null : { duration: 150, easing: 'ease-out' } }),
      PreventSelection,
    ],
    [reducedMotion],
  )

  const endDrag = useRef<(() => void) | null>(null)
  useEffect(() => () => endDrag.current?.(), [])

  /**
   * While any card is dragged, the board's scroll snapping (on phones) is
   * paused: it would pull every small step of the auto-scroller back to the
   * current column, so the board could never scroll to a far column.
   *
   * A keyboard drag moves the card a column at a time, by status order -
   * not by pixels, which on a board narrower than its columns would go
   * wrong: dnd-kit only measures columns that are in view, and the board
   * scrolls (and snaps) under the card. So each Left or Right key picks the
   * next column, scrolls it into view (with snapping paused), measures the
   * columns again, and puts the card on that column's centre; the column
   * under the card is then found as for the pointer. The ends stay put.
   */
  function handleDragStart(event: DragStartEvent, manager: DragDropManager) {
    endDrag.current?.()
    endDrag.current = null
    const board = containerRef.current?.querySelector<HTMLElement>('[data-board-scroller]')
    const ticket = ticketOf(event.operation.source)
    if (!board || !ticket) {
      return
    }
    const snap = board.style.scrollSnapType
    board.style.scrollSnapType = 'none'
    if (!(event.operation.activatorEvent instanceof KeyboardEvent)) {
      endDrag.current = () => {
        board.style.scrollSnapType = snap
      }
      return
    }
    const view = board.ownerDocument.defaultView ?? window

    function step(key: KeyboardEvent) {
      if (key.code !== 'ArrowLeft' && key.code !== 'ArrowRight') {
        return
      }
      key.preventDefault()
      key.stopImmediatePropagation()
      const position = manager.dragOperation.position.current
      if (!manager.dragOperation.status.dragging || !position || !board || !ticket) {
        return
      }
      const current = STATUS_ORDER.indexOf(statusOf(manager.dragOperation.target) ?? ticket.status)
      const wanted = current + (key.code === 'ArrowRight' ? 1 : -1)
      const next = STATUS_ORDER[Math.min(STATUS_ORDER.length - 1, Math.max(0, wanted))]
      const column = board.querySelector(`[data-board-column="${next}"]`)
      if (!column) {
        return
      }
      const boardBox = board.getBoundingClientRect()
      const before = column.getBoundingClientRect()
      if (before.left < boardBox.left) {
        board.scrollLeft += before.left - boardBox.left
      } else if (before.right > boardBox.right) {
        board.scrollLeft += before.right - boardBox.right
      }
      for (const droppable of manager.registry.droppables) {
        droppable.refreshShape()
      }
      const box = column.getBoundingClientRect()
      manager.actions.move({ to: { x: box.left + box.width / 2, y: position.y } })
    }

    view.addEventListener('keydown', step, { capture: true })
    endDrag.current = () => {
      view.removeEventListener('keydown', step, { capture: true })
      board.style.scrollSnapType = snap
    }
  }

  function handleDragEnd(event: DragEndEvent) {
    endDrag.current?.()
    endDrag.current = null
    const { source, target, activatorEvent } = event.operation
    const ticket = ticketOf(source)
    const status = statusOf(target)
    if (event.canceled || !ticket || !status || status === ticket.status) {
      // A keyboard drag that moved nothing may have scrolled the board away
      // from the card, whose handle still has focus: bring it back in view.
      if (activatorEvent instanceof KeyboardEvent) {
        source?.element?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
      }
      return
    }
    onDrop(ticket.id, status, activatorEvent instanceof KeyboardEvent ? 'keyboard' : 'pointer')
  }

  return (
    <div ref={containerRef}>
      <DragDropProvider
        sensors={sensors}
        plugins={plugins}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
      >
        <CancelDragOnChange resetKey={resetKey} />
        {children}
        <DragOverlay dropAnimation={reducedMotion ? null : undefined}>
          {(source) => {
            const ticket = ticketOf(source)
            return ticket ? <BoardCardPreview ticket={ticket} /> : null
          }}
        </DragOverlay>
      </DragDropProvider>
    </div>
  )
}

/** Ends a drag in progress, as cancelled, whenever `resetKey` changes. */
function CancelDragOnChange({ resetKey }: { resetKey: string }) {
  const manager = useDragDropManager()
  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    if (manager && !manager.dragOperation.status.idle) {
      manager.actions.stop({ canceled: true })
    }
  }, [resetKey, manager])
  return null
}
