import { useId, type InputHTMLAttributes, type ReactNode } from 'react'

import { FIELD_CONTROL, FIELD_ERROR, FIELD_HINT, FIELD_LABEL } from './fieldStyles'

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string
  /** The label is read by assistive technology but not drawn (the context already says it). */
  labelHidden?: boolean
  /** Guidance shown under the input (and announced with it). */
  hint?: ReactNode
  error?: string
  /** Rendered inside the input's box, at its end (e.g. a show/hide toggle). */
  trailing?: ReactNode
}

/** A labelled input with optional hint and error, wired up for assistive technology. */
export function TextField({
  label,
  labelHidden = false,
  hint,
  error,
  trailing,
  className = '',
  ...inputProps
}: TextFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={labelHidden ? 'sr-only' : FIELD_LABEL}>
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`${FIELD_CONTROL} h-10 px-3 ${trailing ? 'pr-16' : ''} ${className}`}
          {...inputProps}
        />
        {trailing && <div className="absolute inset-y-0 right-0 flex items-center pr-1.5">{trailing}</div>}
      </div>
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
