import { Pencil, Plus } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'

import { Button } from '../../../components/ui/Button'
import { IconButton } from '../../../components/ui/IconButton'
import { focusAtEnd } from '../../../lib/focusAtEnd'

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
  /**
   * A heading above the text (the description's). The Edit button sits at
   * its end; without one, it sits next to the text.
   */
  heading?: ReactNode
  /** Shown instead of the edit form's own content, e.g. a hidden h1 while the title is edited. */
  whileEditing?: ReactNode
  /** With no value: a quiet button with this text starts editing, instead of Edit. */
  emptyPrompt?: string
  /** Ctrl/⌘+Enter also saves (for multi-line text, where Enter is a new line). */
  saveShortcut?: boolean
}

const IS_MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/.test(navigator.userAgent)

/**
 * A text field of the ticket shown as text with an Edit (pencil) button,
 * edited in place: Save or Cancel (Escape) returns focus to Edit. A failed
 * save stays in editing with the typed text and the error.
 */
export function EditableTicketText({
  field,
  value,
  display,
  renderInput,
  onSave,
  saving,
  busy,
  heading,
  whileEditing,
  emptyPrompt,
  saveShortcut = false,
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
      focusAtEnd(formRef.current?.querySelector<HTMLInputElement | HTMLTextAreaElement>('input, textarea'))
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
    } else if (
      saveShortcut &&
      event.key === 'Enter' &&
      (event.ctrlKey || event.metaKey) &&
      !event.nativeEvent.isComposing
    ) {
      event.preventDefault()
      ;(event.target as HTMLTextAreaElement | HTMLInputElement).form?.requestSubmit()
    }
  }

  const empty = value === '' && emptyPrompt !== undefined
  const editButton = !empty && (
    <IconButton
      ref={editButtonRef}
      icon={Pencil}
      label={`Edit ${field}`}
      onClick={startEditing}
      disabled={busy || editing}
      className={editing ? 'invisible' : ''}
    />
  )

  let body: ReactNode
  if (editing) {
    body = (
      <>
        {whileEditing}
        <form
          ref={formRef}
          noValidate
          aria-busy={saving}
          onSubmit={handleSubmit}
          className="flex animate-fade-in flex-col gap-3"
        >
          {renderInput({
            value: draft,
            onChange: setDraft,
            onKeyDown: handleKeyDown,
            error: error ?? undefined,
            disabled: saving,
          })}
          <div className="flex flex-wrap items-center gap-2">
            <Button type="submit" size="sm" disabled={busy} loading={saving}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
            <Button variant="ghost" size="sm" onClick={cancel} disabled={saving}>
              Cancel
            </Button>
            <span aria-hidden="true" className="ml-auto hidden text-xs text-ink-subtle sm:inline">
              {saveShortcut ? `${IS_MAC ? '⌘' : 'Ctrl'}+Enter to save · ` : 'Enter to save · '}Esc to cancel
            </span>
          </div>
        </form>
      </>
    )
  } else if (empty) {
    body = (
      <button
        ref={editButtonRef}
        type="button"
        onClick={startEditing}
        disabled={busy}
        className="press flex w-full items-center gap-2 rounded-lg border border-dashed border-line-strong px-3 py-3 text-left text-sm text-ink-subtle transition-colors hover:border-ink-subtle/60 hover:bg-surface hover:text-ink-muted disabled:cursor-not-allowed disabled:opacity-60"
      >
        <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
        {emptyPrompt}
      </button>
    )
  } else {
    body = heading ? (
      display
    ) : (
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">{display}</div>
        <div className="shrink-0 pt-0.5 sm:pt-1">{editButton}</div>
      </div>
    )
  }

  if (!heading) {
    return body
  }
  return (
    <div className="flex flex-col gap-2">
      <div className="flex min-h-8 items-center justify-between gap-3">
        {heading}
        {editButton}
      </div>
      {body}
    </div>
  )
}
