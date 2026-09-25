import { useEffect, useRef, useState, type FormEvent } from 'react'

import { createMember, type Member } from '../../../api/members'
import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import { Dialog, DialogFooter } from '../../../components/ui/Dialog'
import { PasswordField } from '../../../components/ui/PasswordField'
import { TextField } from '../../../components/ui/TextField'
import type { FormErrors } from '../../../lib/formErrors'
import { useAuth } from '../../auth/useAuth'
import {
  addMemberErrors,
  EMAIL_MAX_LENGTH,
  NAME_MAX_LENGTH,
  PASSWORD_HINT,
  validateMember,
  type MemberField,
  type MemberFormValues,
} from '../memberForm'

const EMPTY: MemberFormValues = { name: '', email: '', password: '', confirmPassword: '' }
const NO_ERRORS: FormErrors<MemberField> = { form: null, fields: {} }

interface AddMemberDialogProps {
  workspaceId: string
  onClose: () => void
  onAdded: (member: Member) => void
  /** The button that opened the dialog: focus returns to it on close. */
  returnFocus: HTMLElement | null
}

/**
 * An ADMIN creates a member's account directly: QueueFlow sends no email,
 * so the admin chooses the password and passes it on. The password lives
 * only in this dialog's state - kept while it is open (so a failed attempt
 * can be corrected and retried), gone when it closes. Only name, email and
 * password are sent; the backend decides the role (always MEMBER).
 */
export function AddMemberDialog({ workspaceId, onClose, onAdded, returnFocus }: AddMemberDialogProps) {
  const { authorizedRequest } = useAuth()
  const [values, setValues] = useState<MemberFormValues>(EMPTY)
  const [errors, setErrors] = useState<FormErrors<MemberField>>(NO_ERRORS)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)
  const formRef = useRef<HTMLFormElement>(null)
  const [focusRequest, setFocusRequest] = useState(0)

  // After a failed submit: the first field with a message, or else the first
  // field (the form-level Alert announces itself). An effect, so it runs once
  // React has rendered the messages and re-enabled the fields.
  useEffect(() => {
    if (focusRequest === 0) {
      return
    }
    const form = formRef.current
    const target =
      form?.querySelector<HTMLElement>('[aria-invalid="true"]') ?? form?.querySelector<HTMLElement>('input')
    target?.focus()
  }, [focusRequest])

  function update(field: MemberField) {
    return (event: { target: { value: string } }) =>
      setValues((current) => ({ ...current, [field]: event.target.value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    const found = validateMember(values)
    setErrors(found)
    if (Object.keys(found.fields).length > 0) {
      setFocusRequest((current) => current + 1)
      return
    }

    submitting.current = true
    setPending(true)
    try {
      const member = await createMember(authorizedRequest, workspaceId, {
        name: values.name.trim(),
        email: values.email.trim(),
        password: values.password,
      })
      // Nothing of the form outlives it: the parent closes the dialog.
      setValues(EMPTY)
      onAdded(member)
    } catch (error) {
      setErrors(addMemberErrors(error))
      submitting.current = false
      setPending(false)
      setFocusRequest((current) => current + 1)
    }
  }

  return (
    <Dialog
      title="Add member"
      description="Create an account for a teammate. They'll join this workspace as a member."
      onClose={onClose}
      dismissible={!pending}
      returnFocus={returnFocus}
    >
      <form ref={formRef} noValidate aria-busy={pending} onSubmit={handleSubmit}>
        <div className="flex flex-col gap-5 px-5 py-5">
          {errors.form && <Alert tone="error">{errors.form}</Alert>}
          <TextField
            label="Name"
            name="name"
            autoComplete="off"
            maxLength={NAME_MAX_LENGTH}
            value={values.name}
            onChange={update('name')}
            error={errors.fields.name}
            disabled={pending}
            required
            data-autofocus
          />
          <TextField
            label="Email"
            type="email"
            name="email"
            autoComplete="off"
            inputMode="email"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={EMAIL_MAX_LENGTH}
            value={values.email}
            onChange={update('email')}
            hint="They'll use it to sign in."
            error={errors.fields.email}
            disabled={pending}
            required
          />
          <PasswordField
            label="Password"
            name="new-member-password"
            autoComplete="new-password"
            value={values.password}
            onChange={update('password')}
            hint={PASSWORD_HINT}
            error={errors.fields.password}
            disabled={pending}
            required
          />
          <PasswordField
            label="Confirm password"
            name="new-member-password-confirmation"
            autoComplete="new-password"
            value={values.confirmPassword}
            onChange={update('confirmPassword')}
            error={errors.fields.confirmPassword}
            disabled={pending}
            required
          />
          <p className="rounded-md border border-line bg-canvas px-3 py-2.5 text-xs leading-5 text-ink-muted">
            QueueFlow doesn't send emails. Share the email and password with your teammate yourself, through a channel
            you trust. The password can't be shown again or changed in QueueFlow later.
          </p>
        </div>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? 'Adding…' : 'Add member'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  )
}
