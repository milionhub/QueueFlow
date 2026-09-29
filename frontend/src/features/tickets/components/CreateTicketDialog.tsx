import { TicketPlus } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'

import { createTicket, type Ticket, type TicketPriority, type TicketStatus } from '../../../api/tickets'
import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import { Dialog, DialogBody, DialogFieldGroup, DialogFooter } from '../../../components/ui/Dialog'
import { KeyBadge } from '../../../components/ui/PageHeader'
import { SelectField } from '../../../components/ui/SelectField'
import { TextAreaField } from '../../../components/ui/TextAreaField'
import { TextField } from '../../../components/ui/TextField'
import type { FormErrors } from '../../../lib/formErrors'
import { useAuth } from '../../auth/useAuth'
import { useProjectContext } from '../../projects/projectContext'
import { createTicketErrors, type TicketField } from '../ticketErrors'
import { TICKET_PRIORITIES, TICKET_STATUSES } from '../ticketDisplay'

const TITLE_MAX_LENGTH = 255
const NO_ERRORS: FormErrors<TicketField> = { form: null, fields: {} }

interface CreateTicketDialogProps {
  onClose: () => void
  onCreated: (ticket: Ticket) => void
  /** The button that opened the dialog: focus returns to it on close. */
  returnFocus: HTMLElement | null
  /** Where focus goes on close when that button is gone. */
  fallbackFocus?: () => HTMLElement | null
}

/**
 * A new ticket in the current project. The backend decides its number and
 * creator; labels are added from the ticket itself. The title is trimmed
 * here - the backend keeps it as sent.
 */
export function CreateTicketDialog({ onClose, onCreated, returnFocus, fallbackFocus }: CreateTicketDialogProps) {
  const { t } = useTranslation(['tickets', 'common'])
  const { authorizedRequest, user } = useAuth()
  const { project, members } = useProjectContext()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState<TicketStatus>('TODO')
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM')
  const [assigneeId, setAssigneeId] = useState('')
  const [errors, setErrors] = useState<FormErrors<TicketField>>(NO_ERRORS)
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
      form?.querySelector<HTMLElement>('[aria-invalid="true"]') ?? form?.querySelector<HTMLElement>('input, textarea')
    target?.focus()
  }, [focusRequest])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    const trimmedTitle = title.trim()
    const found: FormErrors<TicketField> = { form: null, fields: {} }
    if (trimmedTitle === '') {
      found.fields.title = t('create.enterTitle')
    } else if (trimmedTitle.length > TITLE_MAX_LENGTH) {
      found.fields.title = t('create.titleTooLong', { max: TITLE_MAX_LENGTH })
    }
    setErrors(found)
    if (Object.keys(found.fields).length > 0) {
      setFocusRequest((current) => current + 1)
      return
    }

    submitting.current = true
    setPending(true)
    try {
      const ticket = await createTicket(authorizedRequest, {
        projectId: project.id,
        title: trimmedTitle,
        description: description.trim() === '' ? null : description.trim(),
        status,
        priority,
        assigneeId: assigneeId === '' ? null : assigneeId,
      })
      onCreated(ticket)
    } catch (error) {
      setErrors(createTicketErrors(error))
      submitting.current = false
      setPending(false)
      setFocusRequest((current) => current + 1)
    }
  }

  return (
    <Dialog
      title={t('create.title')}
      icon={TicketPlus}
      description={
        <Trans
          t={t}
          i18nKey="create.inProject"
          values={{ key: project.key, name: project.name }}
          components={{ key: <KeyBadge>{project.key}</KeyBadge> }}
        />
      }
      onClose={onClose}
      dismissible={!pending}
      returnFocus={returnFocus}
      fallbackFocus={fallbackFocus}
      size="lg"
    >
      <form ref={formRef} noValidate aria-busy={pending} onSubmit={handleSubmit}>
        <DialogBody>
          {errors.form && <Alert tone="error">{errors.form}</Alert>}
          <TextField
            label={t('fields.title')}
            name="title"
            autoComplete="off"
            maxLength={TITLE_MAX_LENGTH}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            error={errors.fields.title}
            disabled={pending}
            required
            data-autofocus
          />
          <TextAreaField
            label={t('fields.description')}
            name="description"
            hint={t('fields.optional')}
            rows={3}
            maxRows={10}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            error={errors.fields.description}
            disabled={pending}
          />
          <DialogFieldGroup title={t('create.details')}>
            <div className="grid gap-4 sm:grid-cols-2">
              <SelectField
                label={t('fields.status')}
                name="status"
                value={status}
                onChange={(event) => setStatus(event.target.value as TicketStatus)}
                error={errors.fields.status}
                disabled={pending}
              >
                {TICKET_STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {t(`status.${value}`)}
                  </option>
                ))}
              </SelectField>
              <SelectField
                label={t('fields.priority')}
                name="priority"
                value={priority}
                onChange={(event) => setPriority(event.target.value as TicketPriority)}
                error={errors.fields.priority}
                disabled={pending}
              >
                {TICKET_PRIORITIES.map((value) => (
                  <option key={value} value={value}>
                    {t(`priority.${value}`)}
                  </option>
                ))}
              </SelectField>
            </div>
            <SelectField
              label={t('fields.assignee')}
              name="assigneeId"
              value={assigneeId}
              onChange={(event) => setAssigneeId(event.target.value)}
              error={errors.fields.assigneeId}
              disabled={pending}
            >
              <option value="">{t('common:people.unassigned')}</option>
              {members.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.id === user?.id ? t('common:people.nameYou', { name: member.name }) : member.name}
                </option>
              ))}
            </SelectField>
          </DialogFieldGroup>
        </DialogBody>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" disabled={pending} loading={pending}>
            {pending ? t('create.submitting') : t('create.submit')}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  )
}
