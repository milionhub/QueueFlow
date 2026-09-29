import { UserPlus } from 'lucide-react'
import { useRef, useState, type MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'

import { listMembers, type Member } from '../../../api/members'
import { Button } from '../../../components/ui/Button'
import { Alert } from '../../../components/ui/Alert'
import { LoadError } from '../../../components/ui/LoadError'
import { PageHeader } from '../../../components/ui/PageHeader'
import { StaleNotice } from '../../../components/ui/States'
import { useToast } from '../../../components/ui/toastContext'
import { useResource } from '../../../lib/useResource'
import { useAuth } from '../../auth/useAuth'
import type { CurrentUser } from '../../auth/types'
import { WorkspaceName } from '../../workspace/WorkspaceName'
import { AddMemberDialog } from '../components/AddMemberDialog'
import { EditMemberDialog } from '../components/EditMemberDialog'
import { MemberList, MembersSkeleton } from '../components/MemberList'
import { RemoveMemberDialog } from '../components/RemoveMemberDialog'

/** The member an ADMIN chose from a row's ⋯ menu, and that ⋯ button (focus returns to it). */
interface Managing {
  action: 'edit' | 'remove'
  member: Member
  opener: HTMLElement
}

/**
 * /app/members: everyone in the workspace, for both roles. An ADMIN can
 * also add members, and edit (rename) or remove the other MEMBERs from a
 * row's ⋯ menu; a MEMBER is offered none of these, and the backend refuses
 * them anyway. Loads its own list - ProjectLayout's members belong to a
 * project's pages.
 */
export function MembersPage() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  return <Members user={user} />
}

function Members({ user }: { user: CurrentUser }) {
  const { t } = useTranslation(['members', 'common'])
  const { authorizedRequest } = useAuth()
  const toast = useToast()
  const isAdmin = user.role === 'ADMIN'
  const { state, retry, reload, replace } = useResource(`members:${user.workspaceId}`, (signal) =>
    listMembers(authorizedRequest, user.workspaceId, signal),
  )
  const [dialogOpener, setDialogOpener] = useState<HTMLElement | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [added, setAdded] = useState<string | null>(null)
  const [managing, setManaging] = useState<Managing | null>(null)
  const listRef = useRef<HTMLUListElement>(null)

  function openDialog(event: MouseEvent<HTMLButtonElement>) {
    setDialogOpener(event.currentTarget)
    setDialogOpen(true)
  }

  function handleAdded(member: Member) {
    setDialogOpen(false)
    setAdded(member.name)
    // The backend's order is the list's order: load it again rather than guess where the new member goes.
    reload()
  }

  /** The renamed member shows at once; a reload then puts them where the backend's name order says. */
  function handleSaved(member: Member) {
    setManaging(null)
    if (state.status === 'ready') {
      replace(state.data.map((current) => (current.id === member.id ? member : current)))
    }
    reload()
  }

  function handleRemoved(member: Member) {
    setManaging(null)
    setAdded(null)
    if (state.status === 'ready') {
      replace(state.data.filter((current) => current.id !== member.id))
    }
    toast.show({ message: t('removedToast', { name: member.name }) })
  }

  let content
  switch (state.status) {
    case 'idle':
    case 'loading':
      content = <MembersSkeleton />
      break
    case 'error':
      content = <LoadError message={t('loadError')} reason={state.reason} onRetry={retry} />
      break
    case 'ready':
      content = (
        <div className="flex flex-col gap-3">
          {state.refreshFailed && !state.refreshing && (
            <StaleNotice onRefresh={reload}>{t('common:load.listOutdated')}</StaleNotice>
          )}
          {state.data.length === 0 ? (
            <p className="text-sm text-ink-subtle">{t('empty')}</p>
          ) : (
            <MemberList
              ref={listRef}
              members={state.data}
              currentUserId={user.id}
              actions={
                isAdmin
                  ? {
                      onEdit: (member, opener) => setManaging({ action: 'edit', member, opener }),
                      onRemove: (member, opener) => setManaging({ action: 'remove', member, opener }),
                    }
                  : undefined
              }
            />
          )}
        </div>
      )
      break
  }

  const count = state.status === 'ready' ? state.data.length : null
  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <PageHeader
        title={t('title')}
        description={
          <>
            <p>
              <WorkspaceName />
              {count !== null && (
                <>
                  {' · '}
                  {t('count', { count })}
                </>
              )}
            </p>
            <p className="mt-1 text-xs leading-5">
              {t('roleNote')}
            </p>
          </>
        }
        actions={
          isAdmin && (
            <Button onClick={openDialog}>
              <UserPlus aria-hidden="true" className="size-4" strokeWidth={2} />
              {t('addMember')}
            </Button>
          )
        }
      />
      <div>
        {/* Always mounted, so the message is announced when it appears. It stays: it carries an instruction. */}
        <div role="status">
          {added && (
            <div className="mb-4 animate-enter">
              <Alert tone="info" announce={false}>
                {t('added', { name: added })}
              </Alert>
            </div>
          )}
        </div>
        {content}
      </div>
      {isAdmin && dialogOpen && (
        <AddMemberDialog
          workspaceId={user.workspaceId}
          onClose={() => setDialogOpen(false)}
          onAdded={handleAdded}
          returnFocus={dialogOpener}
        />
      )}
      {isAdmin && managing?.action === 'edit' && (
        <EditMemberDialog
          workspaceId={user.workspaceId}
          member={managing.member}
          onClose={() => setManaging(null)}
          onSaved={handleSaved}
          onMemberGone={reload}
          returnFocus={managing.opener}
          fallbackFocus={() => listRef.current}
        />
      )}
      {isAdmin && managing?.action === 'remove' && (
        <RemoveMemberDialog
          workspaceId={user.workspaceId}
          member={managing.member}
          onClose={() => setManaging(null)}
          onRemoved={handleRemoved}
          returnFocus={managing.opener}
          fallbackFocus={() => listRef.current}
        />
      )}
    </div>
  )
}
