import { useId, useRef, type ReactNode, type TextareaHTMLAttributes } from 'react'

import { useAutoGrow } from '../../lib/useAutoGrow'
import { FIELD_CONTROL, FIELD_ERROR, FIELD_HINT, FIELD_LABEL } from './fieldStyles'

interface TextAreaFieldProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: string
  /** The label is read by assistive technology but not drawn (the context already says it). */
  labelHidden?: boolean
  /** Guidance shown under the text area (and announced with it). */
  hint?: ReactNode
  error?: string
  /** Grows with its content from `rows` up to this many lines, then scrolls. */
  maxRows?: number
}

/** A labelled multi-line input, wired up exactly like TextField. */
export function TextAreaField({
  label,
  labelHidden = false,
  hint,
  error,
  rows = 3,
  maxRows,
  className = '',
  ...textAreaProps
}: TextAreaFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined
  const ref = useRef<HTMLTextAreaElement>(null)
  const autoGrow = maxRows !== undefined
  useAutoGrow(ref, textAreaProps.value, { minRows: rows, maxRows: maxRows ?? rows }, autoGrow)

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={labelHidden ? 'sr-only' : FIELD_LABEL}>
        {label}
      </label>
      <textarea
        ref={ref}
        id={id}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`${FIELD_CONTROL} block px-3 py-2 leading-6 ${autoGrow ? 'resize-none' : 'min-h-20 resize-y'} ${className}`}
        {...textAreaProps}
      />
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
