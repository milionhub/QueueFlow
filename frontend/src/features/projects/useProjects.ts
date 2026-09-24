import { useCallback, useEffect, useState } from 'react'

import { loadFailureOf, type LoadFailure } from '../../api/errors'
import { listProjects, type Project } from '../../api/projects'
import { useAuth } from '../auth/useAuth'

type ProjectsState =
  | { status: 'loading' }
  /**
   * `refreshing`: a reload() is under way while this list stays on screen.
   * `refreshFailed`: the last reload() failed, so the list may be out of date.
   */
  | { status: 'ready'; projects: Project[]; refreshing: boolean; refreshFailed: boolean }
  | { status: 'error'; reason: LoadFailure }

interface ProjectsResult {
  state: ProjectsState
  /** Loads again from scratch, showing the loading state (after an error). */
  retry: () => void
  /** Loads again behind the current list (after a create or an edit). */
  reload: () => void
}

/**
 * The workspace's projects, in the backend's order. A new workspace or a
 * retry starts over; a reload keeps the list on screen until the new one
 * arrives. Every new request aborts the previous one, and so does
 * unmounting. 401 is handled by authorizedRequest.
 */
export function useProjects(workspaceId: string): ProjectsResult {
  const { authorizedRequest } = useAuth()
  const [attempt, setAttempt] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [result, setResult] = useState<{ key: string; refresh: number; state: ProjectsState } | null>(null)
  const key = `${workspaceId}#${attempt}`

  useEffect(() => {
    const controller = new AbortController()
    listProjects(authorizedRequest, workspaceId, controller.signal)
      .then((projects) =>
        setResult({ key, refresh, state: { status: 'ready', projects, refreshing: false, refreshFailed: false } }),
      )
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        setResult((current) =>
          // A failed reload keeps the list it was refreshing.
          current && current.key === key && current.state.status === 'ready'
            ? { key, refresh, state: { ...current.state, refreshing: false, refreshFailed: true } }
            : { key, refresh, state: { status: 'error', reason: loadFailureOf(error) } },
        )
      })
    return () => controller.abort()
  }, [key, refresh, workspaceId, authorizedRequest])

  const retry = useCallback(() => setAttempt((current) => current + 1), [])
  const reload = useCallback(() => setRefresh((current) => current + 1), [])

  let state: ProjectsState = { status: 'loading' }
  if (result && result.key === key) {
    state =
      result.refresh !== refresh && result.state.status === 'ready'
        ? { ...result.state, refreshing: true }
        : result.state
  }
  return { state, retry, reload }
}
