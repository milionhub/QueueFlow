import { UserPen } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'

import { updateMember, type Member } from '../../../api/members'
import { Alert } from '../../../components/ui/Alert'
import { Avatar } from '../../../components/ui/Avatar'
import { Button } from '../../../components/ui/Button'
import { Dialog, DialogBody, DialogFooter } from '../../../components/ui/Dialog'
import { TextField } from '../../../components/ui/TextField'
import type { FormErrors } from '../../../lib/formErrors'
import { useAuth } from '../../auth/useAuth'
import { editMemberErrors, isMemberGone, NAME_MAX_LENGTH } from '../memberForm'

const NO_ERRORS: FormErrors<'name'> = { form: null, fields: {} }

interface EditMemberDialogProps {
  workspaceId: string
  member: Member
  onClose: () => void
  onSaved: (member: Member) => void
  /** The member is no longer in the workspace (404): the list is out of date. */
  onMemberGone: () => void
  /** The ⋯ button that opened the dialog: focus returns to it on close. */
  returnFocus: HTMLElement | null
  /** Where focus goes on close when that button is gone. */
  fallbackFocus?: () => HTMLElement | null
}

/**
 * An ADMIN renames a member - the only thing about a member that can be
 * edited. Who is being edited stays in view (avatar, current name, the
 * email they sign in with), and the email is shown, never editable.
 */
export function EditMemberDialog({
  workspaceId,
  member,
  onClose,
  onSaved,
  onMemberGone,
  returnFocus,
  fallbackFocus,
}: EditMemberDialogProps) {
  const { authorizedRequest } = useAuth()
  const [name, setName] = useState(member.name)
  const [errors, setErrors] = useState<FormErrors<'name'>>(NO_ERRORS)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)
  const formRef = useRef<HTMLFormElement>(null)
  const [focusRequest, setFocusRequest] = useState(0)
  const unchanged = name.trim() === member.name

  // After a failed submit, back to the field (re-enabled by then): its
  // message, or the form-level Alert, says what to fix.
  useEffect(() => {
    if (focusRequest > 0) {
      formRef.current?.querySelector<HTMLInputElement>('input')?.focus()
    }
  }, [focusRequest])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || unchanged) {
      return
    }
    const trimmed = name.trim()
    if (trimmed === '') {
      setErrors({ form: null, fields: { name: 'Enter a name.' } })
      setFocusRequest((current) => current + 1)
      return
    }
    if (trimmed.length > NAME_MAX_LENGTH) {
      setErrors({ form: null, fields: { name: `Name must be at most ${NAME_MAX_LENGTH} characters.` } })
      setFocusRequest((current) => current + 1)
      return
    }

    submitting.current = true
    setPending(true)
    setErrors(NO_ERRORS)
    try {
      onSaved(await updateMember(authorizedRequest, workspaceId, member.id, { name: trimmed }))
    } catch (error) {
      setErrors(editMemberErrors(error))
      if (isMemberGone(error)) {
        onMemberGone()
      }
      submitting.current = false
      setPending(false)
      setFocusRequest((current) => current + 1)
    }
  }

  return (
    <Dialog
      title="Edit member"
      icon={UserPen}
      description="Change how this member's name appears across the workspace."
      onClose={onClose}
      dismissible={!pending}
      returnFocus={returnFocus}
      fallbackFocus={fallbackFocus}
    >
      <form ref={formRef} noValidate aria-busy={pending} onSubmit={handleSubmit}>
        <DialogBody>
          {errors.form && <Alert tone="error">{errors.form}</Alert>}
          <div className="flex items-center gap-3 rounded-lg bg-canvas px-3 py-2.5 ring-1 ring-line ring-inset">
            <Avatar name={member.name} seed={member.id} size="md" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-ink" title={member.name}>
                {member.name}
              </p>
              <p className="truncate text-sm text-ink-muted" title={member.email}>
                <span className="sr-only">Email: </span>
                {member.email}
              </p>
            </div>
          </div>
          <TextField
            label="Name"
            name="name"
            autoComplete="off"
            maxLength={NAME_MAX_LENGTH}
            value={name}
            onChange={(event) => setName(event.target.value)}
            hint="Their email and password stay the same."
            error={errors.fields.name}
            disabled={pending}
            required
            data-autofocus
          />
        </DialogBody>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending || unchanged} loading={pending}>
            {pending ? 'Saving…' : 'Save changes'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  )
}
