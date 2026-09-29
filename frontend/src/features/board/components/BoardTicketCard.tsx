import { useDraggable } from '@dnd-kit/react'
import { GripVertical } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import type { Ticket } from '../../../api/tickets'
import { Avatar, EmptyAvatar } from '../../../components/ui/Avatar'
import { FROM_BOARD, ticketPath } from '../../../routes/paths'
import { LabelChips } from '../../tickets/components/LabelChips'
import { PriorityLabel } from '../../tickets/TicketBadges'
import type { CardDragData } from './BoardDragDrop'

const CARD =
  'flex min-w-0 flex-col gap-2 rounded-lg border border-line bg-surface p-3 shadow-xs transition-[border-color,box-shadow,opacity] duration-150'

interface BoardTicketCardProps {
  ticket: Ticket
  memberName: (userId: string) => string
  /** The ticket's move is being saved: it cannot be dragged until then. */
  saving: boolean
  /** Next to the drag handle: a control of the card (the Move button). */
  headerAction?: ReactNode
  /** Next to the key: a short status of the card ("Saving…"). */
  note?: ReactNode
  /** Below the card's content (the Move destinations, when open). */
  footer?: ReactNode
}

/**
 * One ticket on the board: key, title, up to two labels, and a row with
 * the priority and the assignee's avatar. Only the title is a link - to the
 * ticket's page, remembering the board so its back link returns here. The
 * status is the column's; it is not repeated.
 *
 * The card can be dragged to another column: from anywhere but its link
 * and buttons (see BoardDragDrop for how each input starts a drag), or by
 * its grip handle, which is also where keyboard dragging starts. While it
 * is dragged the card stays in place, faded, and a lifted preview follows
 * the pointer.
 */
export function BoardTicketCard({ ticket, memberName, saving, headerAction, note, footer }: BoardTicketCardProps) {
  const { t } = useTranslation(['board', 'tickets', 'common'])
  const { ref, handleRef, isDragSource } = useDraggable<CardDragData>({
    id: ticket.id,
    data: { ticket },
    disabled: saving,
  })
  const assignee = ticket.assigneeId ? memberName(ticket.assigneeId) : null
  return (
    <div
      ref={ref}
      className={`${CARD} ${saving ? '' : 'cursor-grab hover:border-line-strong hover:shadow-sm'} ${
        isDragSource ? 'border-dashed opacity-40 shadow-none' : ''
      }`}
    >
      <div className="-my-1 flex min-h-8 items-center gap-1 max-sm:-my-1.5">
        <span className="mr-auto flex min-w-0 items-center gap-2">
          <span className="font-mono text-xs whitespace-nowrap text-ink-subtle">{ticket.displayKey}</span>
          {note}
        </span>
        {headerAction}
        <button
          ref={handleRef}
          type="button"
          data-drag-handle={ticket.id}
          aria-label={t('card.drag', { key: ticket.displayKey })}
          aria-disabled={saving || undefined}
          className="-mr-1.5 inline-flex size-8 shrink-0 cursor-grab touch-none items-center justify-center rounded-md text-ink-subtle transition-colors hover:bg-canvas-strong hover:text-ink aria-disabled:cursor-not-allowed aria-disabled:opacity-40 aria-disabled:hover:bg-transparent max-sm:size-11"
        >
          <GripVertical aria-hidden="true" className="size-4" strokeWidth={2} />
        </button>
      </div>
      {/* The clamp is on the wrapper and the link stays inline: only the words
          are the link, and the rest of the row is card - somewhere to drag from. */}
      <p className="line-clamp-3 text-sm leading-5 font-medium break-words">
        <Link
          to={ticketPath(ticket.projectKey, ticket.ticketNumber)}
          state={FROM_BOARD}
          title={ticket.title}
          className="text-ink underline-offset-4 hover:text-accent-strong hover:underline"
        >
          {ticket.title}
        </Link>
      </p>
      {ticket.labels.length > 0 && <LabelChips labels={ticket.labels} limit={2} />}
      <div className="flex min-w-0 items-center justify-between gap-2">
        <PriorityLabel priority={ticket.priority} />
        <span className="shrink-0" title={assignee ?? t('common:people.unassigned')}>
          {assignee && ticket.assigneeId ? (
            <Avatar name={assignee} seed={ticket.assigneeId} size="sm" />
          ) : (
            <EmptyAvatar size="sm" />
          )}
          <span className="sr-only">
            {t('tickets:row.assigneePrefix')}
            {assignee ?? t('common:people.unassigned')}
          </span>
        </span>
      </div>
      {footer}
    </div>
  )
}

/**
 * What follows the pointer during a drag: the card's key, title and
 * priority, lifted, with no link or button (the real card is still on the
 * board). Hidden from assistive technology - the drag is announced instead.
 */
export function BoardCardPreview({ ticket }: { ticket: Ticket }) {
  return (
    <div
      aria-hidden="true"
      className={`${CARD} animate-lift cursor-grabbing border-accent/30 shadow-xl ring-1 ring-accent/20`}
    >
      <span className="font-mono text-xs whitespace-nowrap text-ink-subtle">{ticket.displayKey}</span>
      <p className="line-clamp-3 text-sm leading-5 font-medium break-words text-ink">{ticket.title}</p>
      <div>
        <PriorityLabel priority={ticket.priority} />
      </div>
    </div>
  )
}
