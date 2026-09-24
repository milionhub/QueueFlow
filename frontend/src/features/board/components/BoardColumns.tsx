import { pointerIntersection } from '@dnd-kit/collision'
import { useDragOperation, useDroppable } from '@dnd-kit/react'
import { useId, useMemo, type ReactNode } from 'react'

import type { Ticket, TicketStatus } from '../../../api/tickets'
import { STATUS_LABELS } from '../../tickets/ticketDisplay'
import { groupByStatus, STATUS_ORDER } from '../boardColumns'
import type { CardDragData, ColumnDropData } from './BoardDragDrop'

/**
 * Only the board scrolls sideways, never the page; the page scrolls
 * vertically and the columns grow with their cards. Five columns fit from
 * `xl` (1280px) at 11.5rem or more each; below that each column is 15rem,
 * and on phones 85% of the board's width, so the next one peeks in and
 * columns snap into place. `relative` makes the scroller the containing
 * block of the visually hidden text inside, which would otherwise escape
 * the scroller's clipping and widen the page. On phones the scroller runs
 * to the screen's edges (the page's 16px margin moves inside it), so a
 * finger dragging a card to the edge of the screen is still over the board
 * and auto-scrolls it. The columns of a row share
 * its height (and have a minimum), so all of each column - not just its
 * cards - is somewhere to drop.
 */
export const BOARD_SCROLLER =
  'relative overflow-x-auto overscroll-x-contain pb-2 max-sm:-mx-4 max-sm:snap-x max-sm:snap-mandatory ' +
  'max-sm:scroll-px-4 max-sm:px-4'
export const BOARD_GRID =
  'grid grid-cols-[repeat(5,minmax(85%,1fr))] gap-3 sm:grid-cols-[repeat(5,minmax(15rem,1fr))] ' +
  'xl:grid-cols-[repeat(5,minmax(11.5rem,1fr))]'
export const BOARD_COLUMN =
  'relative flex min-h-40 min-w-0 flex-col gap-2 rounded-md border border-line bg-canvas p-2 max-sm:snap-start'

interface BoardColumnsProps {
  /** The tickets to show (the filtered ones, while filtering). */
  tickets: readonly Ticket[]
  /** While filtering: every ticket, so each column can say "2 of 5". */
  allTickets?: readonly Ticket[]
  /** One ticket's card; the column wraps it in its list item. */
  renderTicket: (ticket: Ticket) => ReactNode
}

/** The five status columns, in workflow order, each with its tickets in ticket-number order. */
export function BoardColumns({ tickets, allTickets, renderTicket }: BoardColumnsProps) {
  const columns = useMemo(() => groupByStatus(tickets), [tickets])
  const totals = useMemo(() => (allTickets ? groupByStatus(allTickets) : null), [allTickets])
  return (
    <div className={BOARD_SCROLLER} data-board-scroller="">
      <div className={BOARD_GRID}>
        {STATUS_ORDER.map((status) => (
          <BoardColumn
            key={status}
            status={status}
            tickets={columns[status]}
            total={totals ? totals[status].length : null}
            renderTicket={renderTicket}
          />
        ))}
      </div>
    </div>
  )
}

/**
 * A column is the drop target only while the pointer is over it - in the
 * part of the board in view. dnd-kit's default would fall back to the
 * column the preview overlaps most, and it measures columns without the
 * board's sideways clipping, so a card released in the gap between two
 * columns, or just outside the board, could land in a neighbouring or
 * hidden column; now it lands nowhere. A keyboard drag moves one column at
 * a time and scrolls it into view, so its position is taken as it is.
 */
const columnUnderPointer: typeof pointerIntersection = (input) => {
  const { dragOperation, droppable } = input
  if (!(dragOperation.activatorEvent instanceof KeyboardEvent)) {
    const pointer = dragOperation.position.current
    // The DOM droppable (the column); the generic type does not name its element.
    const column = (droppable as { element?: Element }).element
    const board = column?.closest('[data-board-scroller]')?.getBoundingClientRect()
    if (
      !pointer ||
      !board ||
      pointer.x < board.left ||
      pointer.x > board.right ||
      pointer.y < board.top ||
      pointer.y > board.bottom
    ) {
      return null
    }
  }
  return pointerIntersection(input)
}

interface BoardColumnProps {
  status: TicketStatus
  tickets: Ticket[]
  /** While filtering: how many tickets the column has in all. */
  total: number | null
  renderTicket: (ticket: Ticket) => ReactNode
}

/**
 * A status: its heading says the count too ("In progress, 2 tickets", or
 * "In progress, 2 of 5 tickets shown" while filtering), then
 * its tickets as a list. The whole column is where a card is dropped to
 * take this status. While a card from another column is over it, it is
 * outlined and says "Drop to move to …" - over the top of its cards, so
 * nothing shifts.
 */
function BoardColumn({ status, tickets, total, renderTicket }: BoardColumnProps) {
  const headingId = useId()
  const { ref, isDropTarget } = useDroppable<ColumnDropData>({
    id: status,
    data: { status },
    collisionDetector: columnUnderPointer,
  })
  const { source } = useDragOperation()
  const dragged = (source?.data as CardDragData | undefined)?.ticket
  const receiving = isDropTarget && dragged !== undefined && dragged.status !== status
  const count = tickets.length
  return (
    <section
      ref={ref}
      aria-labelledby={headingId}
      data-board-column={status}
      className={`${BOARD_COLUMN} ${receiving ? 'border-accent bg-accent-subtle outline-2 outline-accent' : ''}`}
    >
      {receiving && (
        <p
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-2 top-9 z-10 rounded bg-accent px-2 py-1.5 text-center text-xs font-medium text-white shadow-sm"
        >
          Drop to move to {STATUS_LABELS[status]}
        </p>
      )}
      <h2 id={headingId} className="flex items-center justify-between gap-2 px-1 pt-0.5 text-sm font-semibold text-ink">
        <span className="truncate">{STATUS_LABELS[status]}</span>
        <span aria-hidden="true" className="shrink-0 text-xs font-medium text-ink-subtle tabular-nums">
          {total === null ? count : `${count} of ${total}`}
        </span>
        <span className="sr-only">
          {total === null
            ? `, ${count} ${count === 1 ? 'ticket' : 'tickets'}`
            : `, ${count} of ${total} ${total === 1 ? 'ticket' : 'tickets'} shown`}
        </span>
      </h2>
      {count > 0 ? (
        <ul className="flex flex-col gap-2">
          {tickets.map((ticket) => (
            <li key={ticket.id} className="min-w-0">
              {renderTicket(ticket)}
            </li>
          ))}
        </ul>
      ) : (
        <p className="px-1 py-3 text-xs text-ink-subtle">{total ? 'No matching tickets' : 'No tickets'}</p>
      )}
    </section>
  )
}
