import { Link } from 'react-router'

import { BackendStatusBadge } from '../components/BackendStatusBadge'
import { Button } from '../components/ui/Button'
import { useBackendHealth } from '../hooks/useBackendHealth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { env } from '../lib/env'

/** Frontend ↔ backend connectivity check against the public health endpoint. */
export function HealthPage() {
  useDocumentTitle('Backend connectivity')
  const { status, reason, recheck } = useBackendHealth()

  return (
    <section aria-labelledby="health-title" className="max-w-2xl">
      <h1 id="health-title" className="text-2xl font-semibold tracking-tight text-ink">
        Backend connectivity
      </h1>
      <p className="mt-2 text-sm text-ink-muted">Checks the backend's public health endpoint.</p>

      <div className="mt-8 rounded-lg border border-line bg-surface">
        <dl className="divide-y divide-line text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
            <dt className="text-ink-muted">Status</dt>
            <dd aria-live="polite">
              <BackendStatusBadge status={status} />
            </dd>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
            <dt className="text-ink-muted">API base URL</dt>
            <dd className="min-w-0 font-mono text-xs break-all text-ink">{env.apiBaseUrl ?? 'Not configured'}</dd>
          </div>
          {reason && (
            <div className="px-4 py-3">
              <dt className="sr-only">Reason</dt>
              <dd className="text-danger">{reason}</dd>
            </div>
          )}
        </dl>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-4">
        <Button variant="secondary" onClick={recheck} disabled={status === 'checking'}>
          Check again
        </Button>
        <Link to="/" className="text-sm font-medium text-accent underline-offset-4 hover:underline">
          Go to QueueFlow
        </Link>
      </div>
    </section>
  )
}
