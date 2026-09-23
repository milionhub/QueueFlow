import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '../api/errors'
import { getHealth } from '../api/health'

export type BackendStatus = 'checking' | 'connected' | 'unavailable'

export interface BackendHealth {
  status: BackendStatus
  /** A short, user-facing reason when unavailable; never raw error details. */
  reason: string | null
  recheck: () => void
}

/** Checks the backend's public health endpoint on mount and on demand. */
export function useBackendHealth(): BackendHealth {
  const [status, setStatus] = useState<BackendStatus>('checking')
  const [reason, setReason] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    getHealth(controller.signal)
      .then((health) => {
        if (health?.status === 'UP') {
          setStatus('connected')
          setReason(null)
        } else {
          setStatus('unavailable')
          setReason('The backend is running but reports that it is not healthy.')
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        setStatus('unavailable')
        setReason(describe(error))
      })

    return () => controller.abort()
  }, [attempt])

  const recheck = useCallback(() => {
    setStatus('checking')
    setReason(null)
    setAttempt((current) => current + 1)
  }, [])

  return { status, reason, recheck }
}

function describe(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.kind) {
      case 'configuration':
        return error.message
      case 'network':
        return 'The backend could not be reached. Is it running?'
      case 'http':
        return `The backend responded with status ${error.status}.`
    }
  }
  return 'The health check failed unexpectedly.'
}
