import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

import { ApiError } from '../../../api/errors'
import { createLabel, listLabels, type Label } from '../../../api/labels'
import { addTicketLabel, removeTicketLabel, type Ticket } from '../../../api/tickets'
import { Button } from '../../../components/ui/Button'
import { TextField } from '../../../components/ui/TextField'
import { fieldValidationErrors } from '../../../lib/formErrors'
import { useResource } from '../../../lib/useResource'
import { useAuth } from '../../auth/useAuth'
import { ticketChangeError } from '../ticketErrors'
import type { TicketMutation } from '../useTicketMutation'
import { LabelChips } from './LabelChips'

const NAME_MAX_LENGTH = 50
const CREATE = '__create__'

/** The backend's label order: case-insensitive name, then name, then id. */
function byName(a: Label, b: Label): number {
  const lower = a.name.toLowerCase().localeCompare(b.name.toLowerCase())
  if (lower !== 0) {
    return lower
  }
  return a.name === b.name ? a.id.localeCompare(b.id) : a.name < b.name ? -1 : 1
}

interface TicketLabelsProps {
  ticket: Ticket
  mutation: TicketMutation
  announce: (message: string) => void
}

/**
 * The ticket's labels: remove one, add one of the workspace's, or create a
 * new one and add it. The workspace's labels are loaded the first time
 * "Add label" is opened, and kept while the page is open.
 */
export function TicketLabels({ ticket, mutation, announce }: TicketLabelsProps) {
  const { authorizedRequest, user } = useAuth()
  const workspaceId = user?.workspaceId ?? ''
  const [opened, setOpened] = useState(false)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  /** Created, but adding it to the ticket failed: offered again. */
  const [unattached, setUnattached] = useState<Label | null>(null)
  const catalogue = useResource(opened ? `labels:${workspaceId}` : null, (signal) =>
    listLabels(authorizedRequest, workspaceId, signal),
  )
  const addButtonRef = useRef<HTMLButtonElement>(null)
  const pickerRef = useRef<HTMLDivElement>(null)
  const [focusRequest, setFocusRequest] = useState<{ to: 'add' | 'picker' | 'name'; n: number } | null>(null)
  const pickerId = useId()
  const busy = mutation.pending !== null

  const catalogueStatus = catalogue.state.status
  useEffect(() => {
    if (!focusRequest) {
      return
    }
    if (focusRequest.to === 'add') {
      addButtonRef.current?.focus()
    } else if (focusRequest.to === 'name') {
      pickerRef.current?.querySelector<HTMLElement>('input')?.focus()
    } else {
      // While the labels load there is no select yet: focus waits on the
      // picker itself and moves to the select once it appears - unless the
      // user has moved on in the meantime.
      const select = pickerRef.current?.querySelector<HTMLElement>('select')
      if (select && (document.activeElement === pickerRef.current || document.activeElement === document.body)) {
        select.focus()
      } else if (!select) {
        pickerRef.current?.focus()
      }
    }
  }, [focusRequest, catalogueStatus])

  function focus(to: 'add' | 'picker' | 'name') {
    setFocusRequest((current) => ({ to, n: (current?.n ?? 0) + 1 }))
  }

  const attachedIds = new Set(ticket.labels.map((label) => label.id))
  const labels = catalogue.state.status === 'ready' ? catalogue.state.data : []
  const available = labels.filter((label) => !attachedIds.has(label.id))

  function openPicker() {
    setOpened(true)
    setPickerOpen(true)
    setError(null)
    focus('picker')
  }

  function closePicker() {
    setPickerOpen(false)
    setCreating(false)
    setName('')
    setNameError(null)
    focus('add')
  }

  async function attach(label: Label) {
    setError(null)
    const result = await mutation.run(`label:${label.id}`, () => addTicketLabel(authorizedRequest, ticket.id, label.id))
    if (result?.ok) {
      setUnattached((current) => (current?.id === label.id ? null : current))
      announce(`Label ${label.name} added.`)
    } else if (result) {
      setError(`Could not add ${label.name}: ${ticketChangeError(result.error).message}`)
    }
  }

  async function remove(label: Label) {
    setError(null)
    const result = await mutation.run(`label:${label.id}`, () =>
      removeTicketLabel(authorizedRequest, ticket.id, label.id),
    )
    if (result?.ok) {
      announce(`Label ${label.name} removed.`)
      focus(pickerOpen ? 'picker' : 'add')
    } else if (result) {
      setError(`Could not remove ${label.name}: ${ticketChangeError(result.error).message}`)
    }
  }

  function choose(value: string) {
    if (value === CREATE) {
      setCreating(true)
      setNameError(null)
      focus('name')
      return
    }
    const label = labels.find((candidate) => candidate.id === value)
    if (label) {
      void attach(label)
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) {
      return
    }
    const trimmed = name.trim()
    if (trimmed === '') {
      setNameError('Enter a label name.')
      focus('name')
      return
    }
    if (trimmed.length > NAME_MAX_LENGTH) {
      setNameError(`Name must be at most ${NAME_MAX_LENGTH} characters.`)
      focus('name')
      return
    }
    setNameError(null)
    setError(null)
    // Two requests under one lock: create, then add. If only the second
    // fails, the label exists and must be kept (and offered again).
    const step: { created: Label | null } = { created: null }
    const result = await mutation.run('label:create', async () => {
      const created = await createLabel(authorizedRequest, trimmed)
      step.created = created
      if (catalogue.state.status === 'ready') {
        catalogue.replace([...catalogue.state.data, created].sort(byName))
      }
      return addTicketLabel(authorizedRequest, ticket.id, created.id)
    })
    if (result === null) {
      return
    }
    if (result.ok) {
      announce(`Label ${trimmed} created and added.`)
      setCreating(false)
      setName('')
      focus('picker')
      return
    }
    if (step.created) {
      setUnattached(step.created)
      setCreating(false)
      setName('')
      setError(null)
      focus('picker')
      return
    }
    const failure = result.error
    if (failure instanceof ApiError && failure.kind === 'http' && failure.status === 409) {
      setNameError('A label with that name already exists.')
    } else if (failure instanceof ApiError && failure.kind === 'http' && failure.status === 400 && failure.body) {
      const mapped = fieldValidationErrors(failure.body.message, { name: 'Name' })
      setNameError(mapped.fields.name ?? 'That name is not valid.')
    } else {
      setNameError(ticketChangeError(failure).message)
    }
    focus('name')
  }

  const removingId = mutation.pending?.startsWith('label:') ? mutation.pending.slice('label:'.length) : null

  return (
    <div className="flex min-w-0 flex-col gap-2">
      {ticket.labels.length > 0 ? (
        <LabelChips
          labels={ticket.labels}
          onRemove={(label) => void remove(label)}
          removingId={removingId}
          disabled={busy}
        />
      ) : (
        <span className="text-sm text-ink-subtle">None</span>
      )}
      {unattached && (
        <p role="alert" className="text-xs text-danger">
          Label “{unattached.name}” was created but could not be added to this ticket.{' '}
          <button
            type="button"
            onClick={() => void attach(unattached)}
            disabled={busy}
            className="font-medium text-accent underline-offset-4 hover:underline disabled:opacity-60"
          >
            Try again
          </button>
        </p>
      )}
      {error && (
        <p role="alert" className="text-xs font-medium text-danger">
          {error}
        </p>
      )}
      {!pickerOpen ? (
        <div>
          <Button ref={addButtonRef} variant="secondary" size="sm" onClick={openPicker} aria-controls={pickerId}>
            Add label
          </Button>
        </div>
      ) : (
        <div
          id={pickerId}
          ref={pickerRef}
          tabIndex={-1}
          aria-label="Add labels"
          className="flex flex-col gap-2 rounded-md border border-line p-2.5"
        >
          {catalogue.state.status === 'loading' || catalogue.state.status === 'idle' ? (
            <p role="status" className="text-xs text-ink-muted">
              Loading labels…
            </p>
          ) : catalogue.state.status === 'error' ? (
            <p role="alert" className="text-xs text-danger">
              The labels could not be loaded.{' '}
              <button
                type="button"
                onClick={catalogue.retry}
                className="font-medium text-accent underline-offset-4 hover:underline"
              >
                Retry
              </button>
            </p>
          ) : (
            <>
              <label className="sr-only" htmlFor={`${pickerId}-select`}>
                Add a label
              </label>
              <select
                id={`${pickerId}-select`}
                value=""
                onChange={(event) => choose(event.target.value)}
                disabled={busy || creating}
                className="h-9 w-full rounded-md border border-line bg-surface px-2.5 text-sm text-ink shadow-xs hover:border-ink-subtle/50 focus-visible:border-accent disabled:cursor-not-allowed disabled:opacity-60"
              >
                <option value="" disabled>
                  {labels.length === 0
                    ? 'No labels yet'
                    : available.length === 0
                      ? 'Every label is already added'
                      : 'Choose a label…'}
                </option>
                {available.map((label) => (
                  <option key={label.id} value={label.id}>
                    {label.name}
                  </option>
                ))}
                <option value={CREATE}>Create label…</option>
              </select>
              {creating && (
                <form noValidate onSubmit={handleCreate} className="flex flex-col gap-2">
                  <TextField
                    label="New label name"
                    name="labelName"
                    autoComplete="off"
                    maxLength={NAME_MAX_LENGTH}
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Escape' && !busy) {
                        event.preventDefault()
                        setCreating(false)
                        setNameError(null)
                        focus('picker')
                      }
                    }}
                    error={nameError ?? undefined}
                    disabled={mutation.pending === 'label:create'}
                  />
                  <div className="flex gap-2">
                    <Button type="submit" size="sm" disabled={busy}>
                      {mutation.pending === 'label:create' ? 'Creating…' : 'Create and add'}
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => {
                        setCreating(false)
                        setNameError(null)
                        focus('picker')
                      }}
                      disabled={mutation.pending === 'label:create'}
                    >
                      Cancel
                    </Button>
                  </div>
                </form>
              )}
            </>
          )}
          <div>
            <Button variant="secondary" size="sm" onClick={closePicker} disabled={mutation.pending === 'label:create'}>
              Done
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
