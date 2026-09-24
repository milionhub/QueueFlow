import { useEffect, useRef, useState, type FormEvent } from 'react'

import { createProject, updateProject, type Project, type UpdateProjectRequest } from '../../../api/projects'
import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import { Dialog, DialogFooter } from '../../../components/ui/Dialog'
import { TextAreaField } from '../../../components/ui/TextAreaField'
import { TextField } from '../../../components/ui/TextField'
import { useAuth } from '../../auth/useAuth'
import { projectErrors, type ProjectErrors } from '../projectErrors'

const NAME_MAX_LENGTH = 255
const KEY_PATTERN = /^[A-Z0-9]{2,10}$/

const NO_ERRORS: ProjectErrors = { form: null, fields: {}, projectGone: false }

export type ProjectFormMode = { kind: 'create' } | { kind: 'edit'; project: Project }

interface ProjectFormDialogProps {
  mode: ProjectFormMode
  onClose: () => void
  onSaved: (project: Project) => void
  /** The edited project no longer exists (404): the list is out of date. */
  onProjectGone: () => void
  /** The button that opened the dialog: focus returns to it on close. */
  returnFocus: HTMLElement | null
  /** Where focus goes on close when that button is gone. */
  fallbackFocus?: () => HTMLElement | null
}

/** A blank description is no description. */
function normalizeDescription(value: string | null): string | null {
  const trimmed = value?.trim() ?? ''
  return trimmed === '' ? null : trimmed
}

/**
 * Creating a project (name, key, description) or editing one (name and
 * description: the key is fixed). The browser checks presence and the key's
 * format; everything else is the backend's, and its messages land under
 * the fields they concern.
 */
export function ProjectFormDialog({
  mode,
  onClose,
  onSaved,
  onProjectGone,
  returnFocus,
  fallbackFocus,
}: ProjectFormDialogProps) {
  const { authorizedRequest } = useAuth()
  const editing = mode.kind === 'edit' ? mode.project : null
  const [name, setName] = useState(editing?.name ?? '')
  const [key, setKey] = useState('')
  const [description, setDescription] = useState(editing?.description ?? '')
  const [errors, setErrors] = useState<ProjectErrors>(NO_ERRORS)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)
  const formRef = useRef<HTMLFormElement>(null)
  const [focusRequest, setFocusRequest] = useState(0)

  const changes: UpdateProjectRequest = {}
  if (editing) {
    if (name.trim() !== editing.name) {
      changes.name = name.trim()
    }
    if (normalizeDescription(description) !== normalizeDescription(editing.description)) {
      changes.description = normalizeDescription(description)
    }
  }
  const unchanged = editing !== null && Object.keys(changes).length === 0

  // After a failed submit: the first field with a message, or else the first
  // field (the form-level Alert announces itself). An effect, so it runs once
  // React has rendered the messages and re-enabled the fields.
  useEffect(() => {
    if (focusRequest === 0) {
      return
    }
    const form = formRef.current
    const target =
      form?.querySelector<HTMLElement>('[aria-invalid="true"]') ?? form?.querySelector<HTMLElement>('input, textarea')
    target?.focus()
  }, [focusRequest])

  function focusFirstProblem() {
    setFocusRequest((current) => current + 1)
  }

  function validate(): ProjectErrors {
    const found: ProjectErrors = { ...NO_ERRORS, fields: {} }
    const trimmedName = name.trim()
    if (trimmedName === '') {
      found.fields.name = 'Enter a project name.'
    } else if (trimmedName.length > NAME_MAX_LENGTH) {
      found.fields.name = `Name must be at most ${NAME_MAX_LENGTH} characters.`
    }
    if (!editing) {
      const trimmedKey = key.trim()
      if (trimmedKey === '') {
        found.fields.key = 'Enter a project key.'
      } else if (!KEY_PATTERN.test(trimmedKey)) {
        found.fields.key = 'Use 2–10 letters or digits, e.g. CORE.'
      }
    }
    return found
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || unchanged) {
      return
    }
    const found = validate()
    setErrors(found)
    if (Object.keys(found.fields).length > 0) {
      focusFirstProblem()
      return
    }

    submitting.current = true
    setPending(true)
    const submittedKey = key.trim()
    try {
      const saved = editing
        ? await updateProject(authorizedRequest, editing.id, changes)
        : await createProject(authorizedRequest, {
            name: name.trim(),
            key: submittedKey,
            description: normalizeDescription(description),
          })
      onSaved(saved)
    } catch (error) {
      const mapped = projectErrors(error, editing ? 'edit' : 'create', submittedKey)
      setErrors(mapped)
      if (mapped.projectGone) {
        onProjectGone()
      }
      submitting.current = false
      setPending(false)
      focusFirstProblem()
    }
  }

  return (
    <Dialog
      title={editing ? `Edit ${editing.key}` : 'New project'}
      description={editing ? undefined : 'Projects group your tickets. Every project has a short, permanent key.'}
      onClose={onClose}
      dismissible={!pending}
      returnFocus={returnFocus}
      fallbackFocus={fallbackFocus}
    >
      <form ref={formRef} noValidate aria-busy={pending} onSubmit={handleSubmit}>
        <div className="flex flex-col gap-5 px-5 py-5">
          {errors.form && <Alert tone="error">{errors.form}</Alert>}
          <TextField
            label="Name"
            name="name"
            autoComplete="off"
            maxLength={NAME_MAX_LENGTH}
            placeholder="e.g. Core Platform"
            value={name}
            onChange={(event) => setName(event.target.value)}
            error={errors.fields.name}
            disabled={pending}
            required
            data-autofocus
          />
          {editing ? (
            <div>
              <p className="text-sm font-medium text-ink">Key</p>
              <p className="mt-1.5 font-mono text-sm text-ink">{editing.key}</p>
              <p className="mt-1 text-xs leading-5 text-ink-muted">
                Keys can't be changed because they're part of every ticket ID.
              </p>
            </div>
          ) : (
            <TextField
              label="Key"
              name="key"
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              maxLength={10}
              placeholder="CORE"
              className="font-mono"
              value={key}
              onChange={(event) => setKey(event.target.value.toUpperCase())}
              hint="2–10 letters or digits, e.g. CORE. It prefixes ticket IDs (CORE-7) and can't be changed later."
              error={errors.fields.key}
              disabled={pending}
              required
            />
          )}
          <TextAreaField
            label="Description"
            name="description"
            hint="Optional."
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            error={errors.fields.description}
            disabled={pending}
          />
        </div>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending || unchanged}>
            {editing ? (pending ? 'Saving…' : 'Save changes') : pending ? 'Creating…' : 'Create project'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  )
}
