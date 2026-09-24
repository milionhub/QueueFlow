import { useCallback, useEffect, useState } from 'react'

import { getDashboard, type DashboardResponse } from '../../api/dashboard'
import { ApiError } from '../../api/errors'
import { useAuth } from '../auth/useAuth'

export type DashboardState =
  | { status: 'loading' }
  /** `receivedAt`: when the response arrived; relative times ("5m") are measured from it. */
  | { status: 'ready'; dashboard: DashboardResponse; receivedAt: number }
  /** `network`: no response at all; `server`: any failing response. A 401 never lands here for long: it ends the session. */
  | { status: 'error'; reason: 'network' | 'server' }

/**
 * Loads the workspace dashboard with one request, as the signed-in user.
 * A new workspace or a retry starts a new request and aborts the previous
 * one; unmounting aborts too. 401 is handled by authorizedRequest.
 */
export function useDashboard(workspaceId: string): { state: DashboardState; retry: () => void } {
  const { authorizedRequest } = useAuth()
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ key: string; state: DashboardState } | null>(null)
  const key = `${workspaceId}#${attempt}`

  useEffect(() => {
    const controller = new AbortController()
    getDashboard(authorizedRequest, workspaceId, controller.signal)
      .then((dashboard) => setResult({ key, state: { status: 'ready', dashboard, receivedAt: Date.now() } }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        const reason = error instanceof ApiError && error.kind === 'network' ? 'network' : 'server'
        setResult({ key, state: { status: 'error', reason } })
      })
    return () => controller.abort()
  }, [key, workspaceId, authorizedRequest])

  const retry = useCallback(() => setAttempt((current) => current + 1), [])

  // A result for an earlier request (another workspace, or before a retry) does not count.
  const state: DashboardState = result && result.key === key ? result.state : { status: 'loading' }
  return { state, retry }
}
