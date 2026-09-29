import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { ApiError } from '../api/errors'
import { getHealth } from '../api/health'

export type BackendStatus = 'checking' | 'connected' | 'unavailable'

export interface BackendHealth {
  status: BackendStatus
  /** A short, user-facing reason when unavailable, in the interface language; never raw error details. */
  reason: string | null
  recheck: () => void
}

/** Why the backend is unavailable, kept as a fact and put into words when shown (in the current language). */
type Problem =
  | { kind: 'unhealthy' }
  | { kind: 'configuration' }
  | { kind: 'network' }
  | { kind: 'http'; status: number }
  | { kind: 'failed' }

/** Checks the backend's public health endpoint on mount and on demand. */
export function useBackendHealth(): BackendHealth {
  const { t } = useTranslation()
  const [status, setStatus] = useState<BackendStatus>('checking')
  const [problem, setProblem] = useState<Problem | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    getHealth(controller.signal)
      .then((health) => {
        if (health?.status === 'UP') {
          setStatus('connected')
          setProblem(null)
        } else {
          setStatus('unavailable')
          setProblem({ kind: 'unhealthy' })
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        setStatus('unavailable')
        setProblem(classify(error))
      })

    return () => controller.abort()
  }, [attempt])

  const recheck = useCallback(() => {
    setStatus('checking')
    setProblem(null)
    setAttempt((current) => current + 1)
  }, [])

  let reason: string | null = null
  switch (problem?.kind) {
    case 'unhealthy':
      reason = t('health.unhealthy')
      break
    case 'configuration':
      reason = t('health.missingBaseUrl')
      break
    case 'network':
      reason = t('health.unreachable')
      break
    case 'http':
      reason = t('health.httpStatus', { status: problem.status })
      break
    case 'failed':
      reason = t('health.failed')
      break
  }
  return { status, reason, recheck }
}

function classify(error: unknown): Problem {
  if (error instanceof ApiError) {
    switch (error.kind) {
      case 'configuration':
        return { kind: 'configuration' }
      case 'network':
        return { kind: 'network' }
      case 'http':
        return { kind: 'http', status: error.status }
    }
  }
  return { kind: 'failed' }
}
