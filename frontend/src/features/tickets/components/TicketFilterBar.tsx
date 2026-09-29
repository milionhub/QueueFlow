import { Search, SlidersHorizontal, X } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'

import type { Label } from '../../../api/labels'
import type { Member } from '../../../api/members'
import type { TicketPriority, TicketStatus } from '../../../api/tickets'
import { Button } from '../../../components/ui/Button'
import { Dialog, DialogBody, DialogFooter } from '../../../components/ui/Dialog'
import { FIELD_CONTROL, SELECT_APPEARANCE } from '../../../components/ui/fieldStyles'
import { SelectField } from '../../../components/ui/SelectField'
import type { FilterParam, TicketFilters } from '../ticketFilters'
import { PRIORITY_LABELS, STATUS_LABELS } from '../ticketDisplay'

const CONTROL = `${FIELD_CONTROL} h-10 sm:h-9`
const ACTIVE = 'border-accent/50 bg-accent-subtle/50 hover:border-accent/70'

type SelectFilter = Exclude<FilterParam, 'q'>

interface TicketFilterBarProps {
  filters: TicketFilters
  members: Member[]
  currentUserId: string
  /** Labels on the project's tickets. */
  labels: Label[]
  onSearch: (query: string) => void
  onFilter: (name: SelectFilter, value: string | null) => void
  /** Shown while any filter is active: back to the full list. */
  onClear?: () => void
  /** Leaves out the status filter (on the board, the columns are the statuses). */
  hideStatus?: boolean
  /** e.g. "3 of 12 tickets", at the end of the bar. */
  summary?: ReactNode
}

/**
 * Search and the four filters above the list (three on the board). From
 * `sm` everything is in one row, each select with a label (visually hidden:
 * its first option says what it is) and outlined in the accent while it
 * filters. On phones only search stays in the row; the other filters open
 * in a bottom sheet from "Filters", and the ones in use show underneath as
 * chips, each removable.
 */
export function TicketFilterBar({
  filters,
  members,
  currentUserId,
  labels,
  onSearch,
  onFilter,
  onClear,
  hideStatus = false,
  summary,
}: TicketFilterBarProps) {
  const id = useId()
  const [sheetOpener, setSheetOpener] = useState<HTMLElement | null | undefined>(undefined)
  const options = filterOptions({ members, currentUserId, labels, hideStatus })
  const active = options.filter(({ name }) => filters[name] !== null)

  return (
    <div className="flex flex-col gap-2">
      <div role="search" aria-label="Filter tickets" className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 flex-1 sm:max-w-80 sm:min-w-48">
          <label htmlFor={`${id}-q`} className="sr-only">
            Search tickets by title or key
          </label>
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-subtle"
            strokeWidth={2}
          />
          <input
            id={`${id}-q`}
            type="search"
            value={filters.q}
            onChange={(event) => onSearch(event.target.value)}
            placeholder="Search title or key"
            autoComplete="off"
            className={`${CONTROL} pr-3 pl-9 ${filters.q.trim() ? ACTIVE : ''}`}
          />
        </div>

        {options.map(({ name, label, choices }) => (
          <div key={name} className="hidden w-40 sm:block">
            <label htmlFor={`${id}-${name}`} className="sr-only">
              {label}
            </label>
            <select
              id={`${id}-${name}`}
              value={filters[name] ?? ''}
              onChange={(event) => onFilter(name, event.target.value || null)}
              className={`${CONTROL} ${SELECT_APPEARANCE} pl-2.5 ${filters[name] !== null ? ACTIVE : ''}`}
            >
              {choices.map((choice) => (
                <option key={choice.value} value={choice.value}>
                  {choice.label}
                </option>
              ))}
            </select>
          </div>
        ))}
        {onClear && (
          <div className="hidden sm:block">
            <Button variant="ghost" onClick={onClear}>
              <X aria-hidden="true" className="size-4" strokeWidth={2} />
              Clear filters
            </Button>
          </div>
        )}

        <Button
          variant="secondary"
          size="lg"
          aria-haspopup="dialog"
          onClick={(event) => setSheetOpener(event.currentTarget)}
          className={`sm:hidden ${active.length > 0 ? 'border-accent/50! bg-accent-subtle! text-accent!' : ''}`}
        >
          <SlidersHorizontal aria-hidden="true" className="size-4" strokeWidth={2} />
          Filters
          {active.length > 0 && (
            <span className="rounded-full bg-accent px-1.5 text-xs leading-5 font-semibold text-white tabular-nums">
              {active.length}
              <span className="sr-only"> active</span>
            </span>
          )}
        </Button>

        {summary && <p className="ml-auto hidden text-xs text-ink-muted tabular-nums sm:block">{summary}</p>}
      </div>

      {/* Phones: the filters in use, each removable, and a way to clear them all. */}
      {(active.length > 0 || onClear) && (
        <div className="flex flex-wrap items-center gap-1.5 sm:hidden">
          {active.map(({ name, label, choices }) => {
            const chosen = choices.find((choice) => choice.value === filters[name])?.label ?? filters[name]
            return (
              <button
                key={name}
                type="button"
                onClick={() => onFilter(name, null)}
                aria-label={`Remove filter ${label}: ${chosen}`}
                className="press inline-flex h-8 max-w-full items-center gap-1 rounded-full bg-accent-subtle pr-1.5 pl-3 text-xs font-medium text-accent transition-colors hover:bg-accent/15"
              >
                <span className="truncate">
                  {label}: {chosen}
                </span>
                <X aria-hidden="true" className="size-3.5 shrink-0" strokeWidth={2.25} />
              </button>
            )
          })}
          {onClear && (
            <button
              type="button"
              onClick={onClear}
              className="inline-flex h-8 items-center rounded-full px-2.5 text-xs font-medium text-ink-muted underline-offset-4 hover:text-ink hover:underline"
            >
              Clear filters
            </button>
          )}
          {summary && <p className="ml-auto text-xs text-ink-muted tabular-nums">{summary}</p>}
        </div>
      )}

      {sheetOpener !== undefined && (
        <Dialog
          title="Filters"
          icon={SlidersHorizontal}
          description="Show only the tickets that match every filter you choose."
          onClose={() => setSheetOpener(undefined)}
          returnFocus={sheetOpener}
        >
          <DialogBody>
            {options.map(({ name, label, choices }) => (
              <SelectField
                key={name}
                label={label}
                value={filters[name] ?? ''}
                onChange={(event) => onFilter(name, event.target.value || null)}
              >
                {choices.map((choice) => (
                  <option key={choice.value} value={choice.value}>
                    {choice.label}
                  </option>
                ))}
              </SelectField>
            ))}
          </DialogBody>
          <DialogFooter>
            <Button variant="secondary" size="lg" onClick={onClear} disabled={!onClear}>
              Clear filters
            </Button>
            <Button size="lg" onClick={() => setSheetOpener(undefined)}>
              Done
            </Button>
          </DialogFooter>
        </Dialog>
      )}
    </div>
  )
}

interface FilterOption {
  name: SelectFilter
  label: string
  /** The first choice ("") means "no filter" and says what the select is. */
  choices: { value: string; label: string }[]
}

function filterOptions({
  members,
  currentUserId,
  labels,
  hideStatus,
}: {
  members: Member[]
  currentUserId: string
  labels: Label[]
  hideStatus: boolean
}): FilterOption[] {
  const options: FilterOption[] = []
  if (!hideStatus) {
    options.push({
      name: 'status',
      label: 'Status',
      choices: [
        { value: '', label: 'All statuses' },
        { value: 'open', label: 'Open' },
        ...(Object.keys(STATUS_LABELS) as TicketStatus[]).map((status) => ({
          value: status,
          label: STATUS_LABELS[status],
        })),
      ],
    })
  }
  options.push(
    {
      name: 'priority',
      label: 'Priority',
      choices: [
        { value: '', label: 'All priorities' },
        ...(Object.keys(PRIORITY_LABELS) as TicketPriority[]).map((priority) => ({
          value: priority,
          label: PRIORITY_LABELS[priority],
        })),
      ],
    },
    {
      name: 'assignee',
      label: 'Assignee',
      choices: [
        { value: '', label: 'Anyone' },
        { value: 'me', label: 'Me' },
        { value: 'unassigned', label: 'Unassigned' },
        ...members.map((member) => ({
          value: member.id,
          label: member.id === currentUserId ? `${member.name} (you)` : member.name,
        })),
      ],
    },
    {
      name: 'label',
      label: 'Label',
      choices: [{ value: '', label: 'All labels' }, ...labels.map((label) => ({ value: label.id, label: label.name }))],
    },
  )
  return options
}
