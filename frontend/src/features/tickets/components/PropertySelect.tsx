import { useId, useState } from 'react'

const SELECT =
  'h-9 w-full rounded-md border border-line bg-surface px-2.5 text-sm text-ink shadow-xs transition-colors ' +
  'hover:border-ink-subtle/50 focus-visible:border-accent aria-invalid:border-danger ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

export interface PropertyOption {
  value: string
  label: string
}

interface PropertySelectProps {
  label: string
  /** The value the backend last confirmed. */
  value: string
  options: PropertyOption[]
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

/**
 * One editable property of the ticket as a labelled native select that
 * saves as soon as a new value is chosen. While saving it shows the chosen
 * value; if the save fails it goes back to the confirmed one and says why.
 */
export function PropertySelect({ label, value, options, onSave, saving, disabled }: PropertySelectProps) {
  const id = useId()
  const [chosen, setChosen] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const statusId = `${id}-status`

  async function handleChange(next: string) {
    if (next === value) {
      return
    }
    setChosen(next)
    setError(null)
    const failure = await onSave(next)
    setChosen(null)
    setError(failure)
  }

  return (
    <div className="flex min-w-0 flex-col gap-1">
      <dt>
        <label htmlFor={id} className="text-xs font-medium text-ink-subtle">
          {label}
        </label>
      </dt>
      <dd className="flex min-w-0 flex-col gap-1">
        <select
          id={id}
          value={chosen ?? value}
          onChange={(event) => void handleChange(event.target.value)}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          aria-describedby={saving || error ? statusId : undefined}
          className={SELECT}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        {saving && (
          <p id={statusId} className="text-xs text-ink-muted">
            Saving…
          </p>
        )}
        {!saving && error && (
          <p id={statusId} className="text-xs font-medium text-danger">
            {error}
          </p>
        )}
      </dd>
    </div>
  )
}
