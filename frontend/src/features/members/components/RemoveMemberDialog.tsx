import { UserMinus } from 'lucide-react'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { removeMember, type Member } from '../../../api/members'
import { Alert } from '../../../components/ui/Alert'
import { Avatar } from '../../../components/ui/Avatar'
import { Button } from '../../../components/ui/Button'
import { Dialog, DialogBody, DialogFooter } from '../../../components/ui/Dialog'
import { useAuth } from '../../auth/useAuth'
import { isMemberGone, removeMemberError } from '../memberForm'

interface RemoveMemberDialogProps {
  workspaceId: string
  member: Member
  onClose: () => void
  /** Removed now - or already gone (404), which is what was asked for. */
  onRemoved: (member: Member) => void
  /** The ⋯ button that opened the dialog: focus returns to it on cancel. */
  returnFocus: HTMLElement | null
  /** Where focus goes on close when that button is gone (always, after a removal). */
  fallbackFocus?: () => HTMLElement | null
}

/**
 * The explicit confirmation before an ADMIN removes a member: who, and what
 * it means - they lose access at once, their assigned tickets become
 * unassigned, what they did stays, and QueueFlow cannot bring the account
 * back. Cancel has the initial focus, so an unintended Enter removes no one.
 */
export function RemoveMemberDialog({
  workspaceId,
  member,
  onClose,
  onRemoved,
  returnFocus,
  fallbackFocus,
}: RemoveMemberDialogProps) {
  const { t } = useTranslation(['members', 'common'])
  const { authorizedRequest } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)
  const confirmRef = useRef<HTMLButtonElement>(null)

  async function confirm() {
    if (submitting.current) {
      return
    }
    submitting.current = true
    setPending(true)
    setError(null)
    try {
      await removeMember(authorizedRequest, workspaceId, member.id)
      onRemoved(member)
    } catch (failure) {
      if (isMemberGone(failure)) {
        // Already removed (e.g. from another tab): the outcome asked for.
        onRemoved(member)
        return
      }
      setError(removeMemberError(failure))
      submitting.current = false
      setPending(false)
      // The button was disabled while removing, which dropped focus: back to it, to try again.
      requestAnimationFrame(() => confirmRef.current?.focus())
    }
  }

  return (
    <Dialog
      title={t('remove.title')}
      icon={UserMinus}
      tone="danger"
      description={t('remove.description')}
      onClose={onClose}
      dismissible={!pending}
      returnFocus={returnFocus}
      fallbackFocus={fallbackFocus}
    >
      <DialogBody>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex items-center gap-3 rounded-lg bg-canvas px-3 py-2.5 ring-1 ring-line ring-inset">
          <Avatar name={member.name} seed={member.id} size="md" />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-ink" title={member.name}>
              {member.name}
            </p>
            <p className="truncate text-sm text-ink-muted" title={member.email}>
              <span className="sr-only">{t('list.emailPrefix')}</span>
              {member.email}
            </p>
          </div>
        </div>
        <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm leading-6 text-ink-muted marker:text-ink-subtle">
          <li>{t('remove.signIn')}</li>
          <li>{t('remove.unassigned')}</li>
          <li>{t('remove.history')}</li>
          <li>
            <span className="font-medium text-ink">{t('remove.undoStrong')}</span> {t('remove.undoRest')}
          </li>
        </ul>
      </DialogBody>
      <DialogFooter>
        <Button variant="secondary" onClick={onClose} disabled={pending} data-autofocus>
          {t('common:actions.cancel')}
        </Button>
        <Button
          ref={confirmRef}
          variant="danger"
          onClick={() => void confirm()}
          disabled={pending}
          loading={pending}
        >
          {pending ? t('remove.submitting') : t('remove.submit')}
        </Button>
      </DialogFooter>
    </Dialog>
  )
}
