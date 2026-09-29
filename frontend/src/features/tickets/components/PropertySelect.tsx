import { Check } from 'lucide-react'
import { useEffect, useId, useState, type ReactNode } from 'react'

import { Spinner } from '../../../components/ui/Spinner'

export interface PropertyOption {
  value: string
  label: string
}

interface PropertySelectProps {
  label: string
  /** The value the backend last confirmed. */
  value: string
  options: PropertyOption[]
  /** The small picture in front of a value: a status icon, a priority icon, an avatar. */
  icon: (value: string) => ReactNode
  /**
   * Saves a newly chosen value; resolves to an error message, or null on
   * success. Not called when the confirmed value is chosen again.
   */
  onSave: (value: string) => Promise<string | null>
  /** True while this property is being saved. */
  saving: boolean
  /** True while any change of the ticket is being saved (one at a time). */
  disabled: boolean
}

const SAVED_FOR = 1200

/**
 * One editable property of the ticket as a labelled native select (the
 * platform's own picker) that saves as soon as a new value is chosen.
 * Below `xl` it is a compact chip with its label read by assistive
 * technology only; from `xl` a row of the side panel, label on the left.
 * While saving it shows the chosen value and a spinner; a check confirms
 * the save for a moment (the page's live region says it in words). If the
 * save fails it goes back to the confirmed value and says why.
 */
export function PropertySelect({ label, value, options, icon, onSave, saving, disabled }: PropertySelectProps) {
  const id = useId()
  const [chosen, setChosen] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(0)
  const statusId = `${id}-status`

  useEffect(() => {
    if (saved === 0) {
      return
    }
    const timer = window.setTimeout(() => setSaved(0), SAVED_FOR)
    return () => window.clearTimeout(timer)
  }, [saved])

  async function handleChange(next: string) {
    if (next === value) {
      return
    }
    setChosen(next)
    setError(null)
    setSaved(0)
    const failure = await onSave(next)
    setChosen(null)
    setError(failure)
    if (!failure) {
      setSaved((current) => current + 1)
    }
  }

  const shown = chosen ?? value
  const indicator = saving ? (
    <Spinner className="size-3.5 text-ink-muted" />
  ) : saved ? (
    <Check aria-hidden="true" className="size-3.5 animate-fade-in text-success" strokeWidth={2.5} />
  ) : null

  return (
    <div className="flex min-w-0 flex-col gap-1 max-xl:max-w-full xl:grid xl:grid-cols-[5.5rem_minmax(0,1fr)] xl:items-center xl:gap-x-2">
      <dt>
        <label htmlFor={id} className="sr-only xl:not-sr-only xl:text-xs xl:font-medium xl:text-ink-muted">
          {label}
        </label>
      </dt>
      <dd className="min-w-0">
        <div className="relative flex min-w-0 items-center">
          <span className="pointer-events-none absolute left-2.5 flex items-center">{icon(shown)}</span>
          <select
            id={id}
            value={shown}
            onChange={(event) => void handleChange(event.target.value)}
            disabled={disabled}
            aria-invalid={error ? true : undefined}
            aria-describedby={saving || error ? statusId : undefined}
            className={`peer h-9 max-w-full min-w-0 appearance-none truncate rounded-full border border-line bg-surface pr-8 pl-8 text-base text-ink shadow-xs transition-[background-color,border-color] duration-150 [field-sizing:content] hover:border-line-strong hover:bg-canvas focus-visible:border-accent disabled:cursor-not-allowed aria-invalid:border-danger sm:text-sm xl:h-8 xl:w-full xl:rounded-md xl:border-transparent xl:bg-transparent xl:shadow-none xl:[field-sizing:fixed] xl:hover:border-line xl:hover:bg-surface ${
              indicator ? '' : 'select-chevron xl:bg-none xl:hover:select-chevron xl:focus-visible:select-chevron'
            } ${disabled && !saving ? 'opacity-60' : ''}`}
          >
            {options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {indicator && <span className="pointer-events-none absolute right-2.5 flex items-center">{indicator}</span>}
        </div>
        {saving && (
          <span id={statusId} className="sr-only">
            Saving…
          </span>
        )}
        {!saving && error && (
          <p id={statusId} className="mt-1 animate-fade-in text-xs font-medium text-danger">
            {error}
          </p>
        )}
      </dd>
    </div>
  )
}
