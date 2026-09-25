import { useState, type MouseEvent } from 'react'

import { listMembers, type Member } from '../../../api/members'
import { Button } from '../../../components/ui/Button'
import { LoadError } from '../../../components/ui/LoadError'
import { useResource } from '../../../lib/useResource'
import { useAuth } from '../../auth/useAuth'
import type { CurrentUser } from '../../auth/types'
import { WorkspaceName } from '../../workspace/WorkspaceName'
import { AddMemberDialog } from '../components/AddMemberDialog'
import { MemberList, MembersSkeleton } from '../components/MemberList'

/**
 * /app/members: everyone in the workspace, for both roles. An ADMIN can
 * also add a member; nothing else about members can be managed in V1, so
 * nothing else is offered. Loads its own list - ProjectLayout's members
 * belong to a project's pages.
 */
export function MembersPage() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  return <Members user={user} />
}

function Members({ user }: { user: CurrentUser }) {
  const { authorizedRequest } = useAuth()
  const isAdmin = user.role === 'ADMIN'
  const { state, retry, reload } = useResource(`members:${user.workspaceId}`, (signal) =>
    listMembers(authorizedRequest, user.workspaceId, signal),
  )
  const [dialogOpener, setDialogOpener] = useState<HTMLElement | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [added, setAdded] = useState<string | null>(null)

  function openDialog(event: MouseEvent<HTMLButtonElement>) {
    setDialogOpener(event.currentTarget)
    setDialogOpen(true)
  }

  function handleAdded(member: Member) {
    setDialogOpen(false)
    setAdded(
      `${member.name} was added as a member. Share their email and the password you set through a trusted channel.`,
    )
    // The backend's order is the list's order: load it again rather than guess where the new member goes.
    reload()
  }

  let content
  switch (state.status) {
    case 'idle':
    case 'loading':
      content = <MembersSkeleton />
      break
    case 'error':
      content = <LoadError message="The members could not be loaded." reason={state.reason} onRetry={retry} />
      break
    case 'ready': {
      const count = state.data.length
      content = (
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
            <p className="text-sm text-ink-muted">
              <WorkspaceName />
              {' · '}
              {count} {count === 1 ? 'member' : 'members'}
            </p>
            {isAdmin && <Button onClick={openDialog}>Add member</Button>}
          </div>
          <p className="text-xs leading-5 text-ink-muted">
            Admins can create and edit projects and add members. Everyone can work on tickets, the board, labels and
            comments.
          </p>
          {state.refreshFailed && !state.refreshing && (
            <p className="text-sm text-ink-muted">
              The list could not be refreshed and may be out of date.{' '}
              <button
                type="button"
                onClick={reload}
                className="font-medium text-accent underline-offset-4 hover:underline"
              >
                Refresh
              </button>
            </p>
          )}
          {count === 0 ? (
            <p className="text-sm text-ink-subtle">No members found.</p>
          ) : (
            <MemberList members={state.data} currentUserId={user.id} />
          )}
        </div>
      )
      break
    }
  }

  return (
    <div className="max-w-4xl">
      {/* Always mounted, so the message is announced when it appears; styled like Alert's info tone. */}
      <div role="status">
        {added && (
          <p className="mb-4 rounded-md border border-accent/25 bg-accent/5 px-3 py-2.5 text-sm leading-5 text-ink">
            {added}
          </p>
        )}
      </div>
      {content}
      {isAdmin && dialogOpen && (
        <AddMemberDialog
          workspaceId={user.workspaceId}
          onClose={() => setDialogOpen(false)}
          onAdded={handleAdded}
          returnFocus={dialogOpener}
        />
      )}
    </div>
  )
}
