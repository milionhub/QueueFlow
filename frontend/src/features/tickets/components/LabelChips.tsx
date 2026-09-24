import { X } from 'lucide-react'

import type { Label } from '../../../api/labels'

const CHIP =
  'inline-block h-5 max-w-40 truncate rounded border border-line bg-canvas px-1.5 text-xs leading-[1.125rem] ' +
  'text-ink-muted'

interface LabelChipsProps {
  labels: Label[]
  /** Show at most this many, then "+N" for the rest. */
  limit?: number
  /** When given, each chip gets a "Remove label …" button. */
  onRemove?: (label: Label) => void
  /** The label being removed right now. */
  removingId?: string | null
  /** True while any change of the ticket is being saved. */
  disabled?: boolean
}

/** A ticket's labels as small neutral chips, in the order they come (by name). */
export function LabelChips({ labels, limit, onRemove, removingId = null, disabled = false }: LabelChipsProps) {
  const shown = limit === undefined ? labels : labels.slice(0, limit)
  const hidden = labels.length - shown.length
  return (
    <ul aria-label="Labels" className="flex min-w-0 flex-wrap items-center gap-1">
      {shown.map((label) =>
        onRemove ? (
          <li
            key={label.id}
            className="inline-flex h-6 max-w-full items-center gap-0.5 rounded border border-line bg-canvas pl-1.5 text-xs text-ink-muted"
          >
            <span className="truncate" title={label.name}>
              {label.name}
            </span>
            <button
              type="button"
              onClick={() => onRemove(label)}
              disabled={disabled}
              aria-label={`Remove label ${label.name}`}
              className="inline-flex size-6 shrink-0 items-center justify-center rounded text-ink-subtle hover:bg-line/60 hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
            >
              {removingId === label.id ? (
                <span aria-hidden="true" className="text-[10px]">
                  …
                </span>
              ) : (
                <X aria-hidden="true" className="size-3" strokeWidth={2} />
              )}
            </button>
          </li>
        ) : (
          <li key={label.id} className={CHIP} title={label.name}>
            {label.name}
          </li>
        ),
      )}
      {hidden > 0 && (
        <li className="text-xs text-ink-subtle" title={labels.slice(shown.length).map((label) => label.name).join(', ')}>
          +{hidden}
          <span className="sr-only"> more</span>
        </li>
      )}
    </ul>
  )
}
