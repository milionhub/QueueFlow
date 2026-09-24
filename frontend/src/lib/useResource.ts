import { useCallback, useEffect, useRef, useState } from 'react'

import { loadFailureOf, type LoadFailure } from '../api/errors'

export type ResourceState<T> =
  /** Nothing to load (the key is null). */
  | { status: 'idle' }
  | { status: 'loading' }
  /**
   * `receivedAt`: when this data arrived (for relative times).
   * `refreshing`: a reload() is under way while this data stays on screen.
   * `refreshFailed`: the last reload() failed, so the data may be out of date.
   */
  | { status: 'ready'; data: T; receivedAt: number; refreshing: boolean; refreshFailed: boolean }
  /** `error` is kept so a caller can tell a 404 from other failures. */
  | { status: 'error'; error: unknown; reason: LoadFailure }

export interface Resource<T> {
  state: ResourceState<T>
  /** Loads again from scratch, showing the loading state (after an error). */
  retry: () => void
  /** Loads again behind the current data (after a change). */
  reload: () => void
  /** Swaps in data the caller already has (e.g. the response of an update). */
  replace: (data: T) => void
}

/**
 * One piece of server data for the current page. `key` names what is
 * loaded (null: nothing yet); a new key or a retry starts over, a reload
 * keeps the current data on screen. Every new request aborts the previous
 * one, and so does unmounting, so a late response never overwrites newer
 * data. `load` is read when the request starts: it need not be stable.
 */
export function useResource<T>(key: string | null, load: (signal: AbortSignal) => Promise<T>): Resource<T> {
  const [attempt, setAttempt] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [result, setResult] = useState<{ key: string; refresh: number; state: ResourceState<T> } | null>(null)
  const loadRef = useRef(load)
  useEffect(() => {
    loadRef.current = load
  })
  const requestKey = key === null ? null : `${key}#${attempt}`

  useEffect(() => {
    if (requestKey === null) {
      return
    }
    const controller = new AbortController()
    loadRef
      .current(controller.signal)
      .then((data) =>
        setResult({
          key: requestKey,
          refresh,
          state: { status: 'ready', data, receivedAt: Date.now(), refreshing: false, refreshFailed: false },
        }),
      )
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        setResult((current) =>
          // A failed reload keeps the data it was refreshing.
          current && current.key === requestKey && current.state.status === 'ready'
            ? { key: requestKey, refresh, state: { ...current.state, refreshing: false, refreshFailed: true } }
            : { key: requestKey, refresh, state: { status: 'error', error, reason: loadFailureOf(error) } },
        )
      })
    return () => controller.abort()
  }, [requestKey, refresh])

  const retry = useCallback(() => setAttempt((current) => current + 1), [])
  const reload = useCallback(() => setRefresh((current) => current + 1), [])
  const replace = useCallback(
    (data: T) =>
      setResult((current) =>
        current && current.state.status === 'ready'
          ? { ...current, state: { ...current.state, data, receivedAt: Date.now() } }
          : current,
      ),
    [],
  )

  let state: ResourceState<T> = requestKey === null ? { status: 'idle' } : { status: 'loading' }
  if (requestKey !== null && result && result.key === requestKey) {
    state =
      result.refresh !== refresh && result.state.status === 'ready'
        ? { ...result.state, refreshing: true }
        : result.state
  }
  return { state, retry, reload, replace }
}
