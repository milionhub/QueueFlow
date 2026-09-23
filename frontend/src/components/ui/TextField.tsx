import { useId, type InputHTMLAttributes, type ReactNode } from 'react'

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string
  /** Guidance shown under the input (and announced with it). */
  hint?: ReactNode
  error?: string
  /** Rendered inside the input's box, at its end (e.g. a show/hide toggle). */
  trailing?: ReactNode
}

const inputClasses =
  'h-10 w-full rounded-md border border-line bg-surface px-3 text-sm text-ink shadow-xs transition-colors ' +
  'placeholder:text-ink-subtle hover:border-ink-subtle/50 focus-visible:border-accent ' +
  'aria-invalid:border-danger disabled:cursor-not-allowed disabled:opacity-60'

/** A labelled input with optional hint and error, wired up for assistive technology. */
export function TextField({ label, hint, error, trailing, className = '', ...inputProps }: TextFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`${inputClasses} ${trailing ? 'pr-16' : ''} ${className}`}
          {...inputProps}
        />
        {trailing && <div className="absolute inset-y-0 right-0 flex items-center pr-1.5">{trailing}</div>}
      </div>
      {hint && (
        <p id={hintId} className="text-xs leading-5 text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs leading-5 font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
