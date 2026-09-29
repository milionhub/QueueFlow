import { useId, type ReactNode, type SelectHTMLAttributes } from 'react'

import { FIELD_CONTROL, FIELD_ERROR, FIELD_HINT, FIELD_LABEL, SELECT_APPEARANCE } from './fieldStyles'

interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  label: string
  /** Guidance shown under the select (and announced with it). */
  hint?: ReactNode
  error?: string
  /** The <option> elements. */
  children: ReactNode
}

/** A labelled native select, wired up exactly like TextField. */
export function SelectField({ label, hint, error, children, className = '', ...selectProps }: SelectFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={FIELD_LABEL}>
        {label}
      </label>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`${FIELD_CONTROL} ${SELECT_APPEARANCE} h-10 pl-3 ${className}`}
        {...selectProps}
      >
        {children}
      </select>
      {hint && (
        <p id={hintId} className={FIELD_HINT}>
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className={FIELD_ERROR}>
          {error}
        </p>
      )}
    </div>
  )
}
