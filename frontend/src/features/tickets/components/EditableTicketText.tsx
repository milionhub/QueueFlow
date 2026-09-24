import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'

import { Button } from '../../../components/ui/Button'

interface EditableTicketTextProps {
  /** The field's name, for the Edit button's label and the error ("title", "description"). */
  field: string
  /** The confirmed value, as text for the input ("" for no description). */
  value: string
  /** How the confirmed value is shown when not editing. */
  display: ReactNode
  /** The input shown while editing; it must label itself and use `error`. */
  renderInput: (props: {
    value: string
    onChange: (value: string) => void
    onKeyDown: (event: KeyboardEvent) => void
    error: string | undefined
    disabled: boolean
  }) => ReactNode
  /**
   * Checks and saves the edited text. Resolves to an error message, or null
   * when saved - or when there was nothing to save.
   */
  onSave: (value: string) => Promise<string | null>
  /** True while this field is being saved. */
  saving: boolean
  /** True while any change of the ticket is being saved (one at a time). */
  busy: boolean
  /** Where the Edit button sits: next to the text, or above it. */
  layout?: 'inline' | 'stacked'
}

/**
 * A text field of the ticket shown as text with an Edit button, edited in
 * place: Save or Cancel (Escape) returns focus to Edit. A failed save stays
 * in editing with the typed text and the error.
 */
export function EditableTicketText({
  field,
  value,
  display,
  renderInput,
  onSave,
  saving,
  busy,
  layout = 'inline',
}: EditableTicketTextProps) {
  const [draft, setDraft] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const editButtonRef = useRef<HTMLButtonElement>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const [focusTarget, setFocusTarget] = useState<{ to: 'input' | 'edit'; request: number } | null>(null)
  const editing = draft !== null

  useEffect(() => {
    if (!focusTarget) {
      return
    }
    if (focusTarget.to === 'edit') {
      editButtonRef.current?.focus()
    } else {
      formRef.current?.querySelector<HTMLElement>('input, textarea')?.focus()
    }
  }, [focusTarget])

  function focus(to: 'input' | 'edit') {
    setFocusTarget((current) => ({ to, request: (current?.request ?? 0) + 1 }))
  }

  function startEditing() {
    setDraft(value)
    setError(null)
    focus('input')
  }

  function cancel() {
    if (saving) {
      return
    }
    setDraft(null)
    setError(null)
    focus('edit')
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (draft === null || busy) {
      return
    }
    const failure = await onSave(draft)
    if (failure) {
      setError(failure)
      focus('input')
      return
    }
    setDraft(null)
    setError(null)
    focus('edit')
  }

  function handleKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault()
      cancel()
    }
  }

  if (editing) {
    return (
      <form ref={formRef} noValidate aria-busy={saving} onSubmit={handleSubmit} className="flex flex-col gap-3">
        {renderInput({
          value: draft,
          onChange: setDraft,
          onKeyDown: handleKeyDown,
          error: error ?? undefined,
          disabled: saving,
        })}
        <div className="flex gap-2">
          <Button type="submit" size="sm" disabled={busy}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
          <Button variant="secondary" size="sm" onClick={cancel} disabled={saving}>
            Cancel
          </Button>
        </div>
      </form>
    )
  }

  const editButton = (
    <Button
      ref={editButtonRef}
      variant="secondary"
      size="sm"
      onClick={startEditing}
      disabled={busy}
      aria-label={`Edit ${field}`}
      className="shrink-0"
    >
      Edit
    </Button>
  )
  return layout === 'inline' ? (
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0 flex-1">{display}</div>
      {editButton}
    </div>
  ) : (
    <div className="flex flex-col gap-2">
      {display}
      <div>{editButton}</div>
    </div>
  )
}
