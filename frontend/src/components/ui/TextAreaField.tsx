import { useId, type ReactNode, type TextareaHTMLAttributes } from 'react'

interface TextAreaFieldProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: string
  /** Guidance shown under the text area (and announced with it). */
  hint?: ReactNode
  error?: string
}

const textAreaClasses =
  'block min-h-20 w-full resize-y rounded-md border border-line bg-surface px-3 py-2 text-sm leading-6 text-ink ' +
  'shadow-xs transition-colors placeholder:text-ink-subtle hover:border-ink-subtle/50 focus-visible:border-accent ' +
  'aria-invalid:border-danger disabled:cursor-not-allowed disabled:opacity-60'

/** A labelled multi-line input, wired up exactly like TextField. */
export function TextAreaField({ label, hint, error, rows = 3, className = '', ...textAreaProps }: TextAreaFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <textarea
        id={id}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`${textAreaClasses} ${className}`}
        {...textAreaProps}
      />
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
