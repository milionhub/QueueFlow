import { useId, type ReactNode } from 'react'

import type { Label } from '../../../api/labels'
import type { Member } from '../../../api/members'
import type { TicketPriority, TicketStatus } from '../../../api/tickets'
import { Button } from '../../../components/ui/Button'
import type { FilterParam, TicketFilters } from '../ticketFilters'
import { PRIORITY_LABELS, STATUS_LABELS } from '../ticketDisplay'

const CONTROL =
  'h-9 w-full rounded-md border border-line bg-surface px-2.5 text-sm text-ink shadow-xs transition-colors ' +
  'placeholder:text-ink-subtle hover:border-ink-subtle/50 focus-visible:border-accent'

interface TicketFilterBarProps {
  filters: TicketFilters
  members: Member[]
  currentUserId: string
  /** Labels on the project's tickets. */
  labels: Label[]
  onSearch: (query: string) => void
  onFilter: (name: Exclude<FilterParam, 'q'>, value: string | null) => void
  /** Shown while any filter is active: back to the full list. */
  onClear?: () => void
}

/**
 * Search and the four filters above the list. Every control has a label
 * (visually hidden: the first option or the placeholder says what it is).
 * One row when there is room; on phones search takes the full width and
 * the selects go two by two.
 */
export function TicketFilterBar({
  filters,
  members,
  currentUserId,
  labels,
  onSearch,
  onFilter,
  onClear,
}: TicketFilterBarProps) {
  const id = useId()
  return (
    <div role="search" aria-label="Filter tickets" className="flex flex-wrap gap-2">
      <div className="basis-full sm:min-w-48 sm:flex-1 sm:basis-auto">
        <label htmlFor={`${id}-q`} className="sr-only">
          Search tickets by title or key
        </label>
        <input
          id={`${id}-q`}
          type="search"
          value={filters.q}
          onChange={(event) => onSearch(event.target.value)}
          placeholder="Search title or key"
          autoComplete="off"
          className={CONTROL}
        />
      </div>
      <FilterSelect
        id={`${id}-status`}
        label="Status"
        value={filters.status ?? ''}
        onChange={(value) => onFilter('status', value)}
      >
        <option value="">All statuses</option>
        <option value="open">Open</option>
        {(Object.keys(STATUS_LABELS) as TicketStatus[]).map((status) => (
          <option key={status} value={status}>
            {STATUS_LABELS[status]}
          </option>
        ))}
      </FilterSelect>
      <FilterSelect
        id={`${id}-priority`}
        label="Priority"
        value={filters.priority ?? ''}
        onChange={(value) => onFilter('priority', value)}
      >
        <option value="">All priorities</option>
        {(Object.keys(PRIORITY_LABELS) as TicketPriority[]).map((priority) => (
          <option key={priority} value={priority}>
            {PRIORITY_LABELS[priority]}
          </option>
        ))}
      </FilterSelect>
      <FilterSelect
        id={`${id}-assignee`}
        label="Assignee"
        value={filters.assignee ?? ''}
        onChange={(value) => onFilter('assignee', value)}
      >
        <option value="">Anyone</option>
        <option value="me">Me</option>
        <option value="unassigned">Unassigned</option>
        {members.map((member) => (
          <option key={member.id} value={member.id}>
            {member.id === currentUserId ? `${member.name} (you)` : member.name}
          </option>
        ))}
      </FilterSelect>
      <FilterSelect
        id={`${id}-label`}
        label="Label"
        value={filters.label ?? ''}
        onChange={(value) => onFilter('label', value)}
      >
        <option value="">All labels</option>
        {labels.map((label) => (
          <option key={label.id} value={label.id}>
            {label.name}
          </option>
        ))}
      </FilterSelect>
      {onClear && (
        <Button variant="secondary" onClick={onClear} className="h-9 basis-full sm:basis-auto">
          Clear filters
        </Button>
      )}
    </div>
  )
}

interface FilterSelectProps {
  id: string
  label: string
  value: string
  onChange: (value: string | null) => void
  children: ReactNode
}

function FilterSelect({ id, label, value, onChange, children }: FilterSelectProps) {
  return (
    <div className="min-w-0 flex-1 basis-[calc(50%-0.25rem)] sm:w-40 sm:flex-none sm:basis-auto">
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <select id={id} value={value} onChange={(event) => onChange(event.target.value || null)} className={CONTROL}>
        {children}
      </select>
    </div>
  )
}
