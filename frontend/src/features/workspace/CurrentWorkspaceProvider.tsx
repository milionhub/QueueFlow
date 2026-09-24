import { useEffect, useState, type ReactNode } from 'react'

import { getWorkspace } from '../../api/workspaces'
import { useAuth } from '../auth/useAuth'
import { CurrentWorkspaceContext, type CurrentWorkspace } from './currentWorkspaceContext'

/**
 * Loads the signed-in user's workspace once for the whole application
 * shell. The current user only carries its id; the name comes from
 * GET /api/workspaces/{id}. A failure is not fatal (see CurrentWorkspace).
 */
export function CurrentWorkspaceProvider({ children }: { children: ReactNode }) {
  const { user, authorizedRequest } = useAuth()
  const workspaceId = user?.workspaceId
  const [result, setResult] = useState<{ id: string; value: CurrentWorkspace } | null>(null)

  useEffect(() => {
    if (!workspaceId) {
      return
    }
    const controller = new AbortController()
    getWorkspace(authorizedRequest, workspaceId, controller.signal)
      .then((workspace) => setResult({ id: workspaceId, value: { status: 'ready', workspace } }))
      .catch(() => {
        if (!controller.signal.aborted) {
          setResult({ id: workspaceId, value: { status: 'unavailable', workspace: null } })
        }
      })
    return () => controller.abort()
  }, [workspaceId, authorizedRequest])

  // A result for another workspace (e.g. after signing in as someone else) does not count.
  const value: CurrentWorkspace =
    result && result.id === workspaceId ? result.value : { status: 'loading', workspace: null }

  return <CurrentWorkspaceContext value={value}>{children}</CurrentWorkspaceContext>
}
