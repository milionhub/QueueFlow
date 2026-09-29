import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { Label } from '../../../api/labels'
import { Spinner } from '../../../components/ui/Spinner'
import { labelDotClass } from '../ticketDisplay'

const CHIP =
  'inline-flex h-5 max-w-40 min-w-0 items-center gap-1.5 rounded-full bg-canvas-strong px-2 text-xs text-ink-muted'

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

function LabelDot({ name }: { name: string }) {
  return <span aria-hidden="true" className={`size-1.5 shrink-0 rounded-full ${labelDotClass(name)}`} />
}

/**
 * A ticket's labels as soft chips, in the order they come (by name). The
 * dot's colour follows the label's name; the name itself is the meaning.
 */
export function LabelChips({ labels, limit, onRemove, removingId = null, disabled = false }: LabelChipsProps) {
  const { t } = useTranslation('tickets')
  const shown = limit === undefined ? labels : labels.slice(0, limit)
  const hidden = labels.length - shown.length
  return (
    <ul aria-label={t('labelChips.list')} className="flex min-w-0 flex-wrap items-center gap-1">
      {shown.map((label) =>
        onRemove ? (
          <li
            key={label.id}
            className="inline-flex h-7 max-w-full min-w-0 items-center gap-1.5 rounded-full bg-canvas-strong pr-0.5 pl-2.5 text-xs text-ink-muted pointer-coarse:h-9"
          >
            <LabelDot name={label.name} />
            <span className="truncate" title={label.name}>
              {label.name}
            </span>
            <button
              type="button"
              onClick={() => onRemove(label)}
              disabled={disabled}
              aria-label={t('labelChips.remove', { name: label.name })}
              className="press inline-flex size-6 shrink-0 items-center justify-center rounded-full text-ink-subtle transition-colors hover:bg-line hover:text-ink disabled:cursor-not-allowed disabled:opacity-60 pointer-coarse:size-8"
            >
              {removingId === label.id ? (
                <Spinner className="size-3" />
              ) : (
                <X aria-hidden="true" className="size-3" strokeWidth={2.25} />
              )}
            </button>
          </li>
        ) : (
          <li key={label.id} className={CHIP} title={label.name}>
            <LabelDot name={label.name} />
            <span className="truncate">{label.name}</span>
          </li>
        ),
      )}
      {hidden > 0 && (
        <li
          className="inline-flex h-5 items-center rounded-full px-1.5 text-xs text-ink-subtle tabular-nums"
          title={labels
            .slice(shown.length)
            .map((label) => label.name)
            .join(', ')}
        >
          +{hidden}
          <span className="sr-only">
            {t('labelChips.more', {
              names: labels
                .slice(shown.length)
                .map((label) => label.name)
                .join(', '),
            })}
          </span>
        </li>
      )}
    </ul>
  )
}
