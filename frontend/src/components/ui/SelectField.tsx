import { useId, type ReactNode, type SelectHTMLAttributes } from 'react'

interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  label: string
  /** Guidance shown under the select (and announced with it). */
  hint?: ReactNode
  error?: string
  /** The <option> elements. */
  children: ReactNode
}

const selectClasses =
  'h-10 w-full rounded-md border border-line bg-surface px-3 text-sm text-ink shadow-xs transition-colors ' +
  'hover:border-ink-subtle/50 focus-visible:border-accent aria-invalid:border-danger ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

/** A labelled native select, wired up exactly like TextField. */
export function SelectField({ label, hint, error, children, className = '', ...selectProps }: SelectFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`${selectClasses} ${className}`}
        {...selectProps}
      >
        {children}
      </select>
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
