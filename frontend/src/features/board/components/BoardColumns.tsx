import { pointerIntersection } from '@dnd-kit/collision'
import { useDragOperation, useDroppable } from '@dnd-kit/react'
import { useEffect, useId, useMemo, useRef, useState, type ReactNode, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'

import type { Ticket, TicketStatus } from '../../../api/tickets'
import { StatusIcon } from '../../tickets/TicketBadges'
import { STATUS_TONE } from '../../tickets/ticketDisplay'
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
  'relative flex min-h-40 min-w-0 flex-col gap-2 rounded-lg border border-line bg-canvas-strong/60 p-2 max-sm:snap-start'

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
  const scrollerRef = useRef<HTMLDivElement>(null)
  return (
    <>
      <ColumnJump
        scrollerRef={scrollerRef}
        counts={STATUS_ORDER.map((status) => ({ status, count: columns[status].length }))}
      />
      <div ref={scrollerRef} className={BOARD_SCROLLER} data-board-scroller="">
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
    </>
  )
}

/**
 * Phones only: a row of the five columns with their counts, above the
 * board. Each jumps the board to its column; the one in view is marked
 * (aria-current). Outside the board's scroller, so dragging is unaffected.
 */
function ColumnJump({
  scrollerRef,
  counts,
}: {
  scrollerRef: RefObject<HTMLDivElement | null>
  counts: { status: TicketStatus; count: number }[]
}) {
  const { t } = useTranslation(['board', 'tickets'])
  const [inView, setInView] = useState<TicketStatus>(STATUS_ORDER[0])

  useEffect(() => {
    const scroller = scrollerRef.current
    if (!scroller) {
      return
    }
    let frame = 0
    function update() {
      frame = 0
      if (!scroller) {
        return
      }
      const left = scroller.getBoundingClientRect().left
      let nearest: TicketStatus = STATUS_ORDER[0]
      let distance = Infinity
      for (const column of scroller.querySelectorAll<HTMLElement>('[data-board-column]')) {
        const offset = Math.abs(column.getBoundingClientRect().left - left - 16)
        if (offset < distance) {
          distance = offset
          nearest = column.dataset.boardColumn as TicketStatus
        }
      }
      setInView(nearest)
    }
    function onScroll() {
      if (!frame) {
        frame = window.requestAnimationFrame(update)
      }
    }
    scroller.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      scroller.removeEventListener('scroll', onScroll)
      window.cancelAnimationFrame(frame)
    }
  }, [scrollerRef])

  function jump(status: TicketStatus) {
    const scroller = scrollerRef.current
    const column = scroller?.querySelector<HTMLElement>(`[data-board-column="${status}"]`)
    if (!scroller || !column) {
      return
    }
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const left = column.getBoundingClientRect().left - scroller.getBoundingClientRect().left + scroller.scrollLeft
    scroller.scrollTo({ left: left - 16, behavior: reduced ? 'auto' : 'smooth' })
  }

  return (
    <nav aria-label={t('columnsNav')} className="-mx-4 overflow-x-auto px-4 sm:hidden">
      <ul className="flex w-max gap-1.5 pb-1">
        {counts.map(({ status, count }) => (
          <li key={status}>
            <button
              type="button"
              aria-current={inView === status ? 'true' : undefined}
              onClick={() => jump(status)}
              className={`press inline-flex h-9 items-center gap-1.5 rounded-full border px-3 text-xs font-medium whitespace-nowrap transition-colors ${
                inView === status
                  ? 'border-ink/15 bg-surface text-ink shadow-xs'
                  : 'border-transparent bg-canvas-strong text-ink-muted hover:text-ink'
              }`}
            >
              <StatusIcon status={status} className="size-3" />
              {t(`tickets:status.${status}`)}
              <span className="text-ink-subtle tabular-nums">{count}</span>
            </button>
          </li>
        ))}
      </ul>
    </nav>
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
  const { t } = useTranslation(['board', 'tickets'])
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
      className={`${BOARD_COLUMN} transition-colors duration-150 ${STATUS_TONE[status].rule} ${receiving ? 'border-accent bg-accent-subtle outline-2 outline-accent' : ''}`}
    >
      {receiving && (
        <p
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-2 top-9 z-10 animate-fade-in rounded-md bg-accent px-2 py-1.5 text-center text-xs font-medium text-white shadow-md"
        >
          {t('column.dropHint', { status: t(`tickets:status.${status}`) })}
        </p>
      )}
      <h2 id={headingId} className="flex items-center gap-2 px-1 pt-0.5 text-sm font-semibold text-ink">
        <StatusIcon status={status} />
        <span className="min-w-0 truncate">{t(`tickets:status.${status}`)}</span>
        <span
          aria-hidden="true"
          className={`ml-auto shrink-0 rounded-full px-2 text-xs leading-5 font-semibold tabular-nums ${STATUS_TONE[status].soft}`}
        >
          {total === null ? count : t('column.countOf', { count, total })}
        </span>
        <span className="sr-only">
          {total === null
            ? t('column.count', { count })
            : t('column.filteredCount', { shown: count, count: total })}
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
        <p className="flex min-h-20 items-center justify-center rounded-md border border-dashed border-line-strong px-2 text-center text-xs text-ink-subtle">
          {total ? t('column.noMatching') : t('column.empty')}
        </p>
      )}
    </section>
  )
}
