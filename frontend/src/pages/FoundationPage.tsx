import { Link } from 'react-router'

import { BackendStatusBadge } from '../components/BackendStatusBadge'
import { useBackendHealth } from '../hooks/useBackendHealth'

/** Temporary home page for Phase 3.1; replaced once authentication exists. */
export function FoundationPage() {
  const { status } = useBackendHealth()

  return (
    <section aria-labelledby="foundation-title" className="max-w-2xl">
      <p className="text-sm font-medium text-accent">Phase 3 · Frontend foundation</p>
      <h1 id="foundation-title" className="mt-2 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
        QueueFlow
      </h1>
      <p className="mt-4 text-base leading-7 text-ink-muted">
        Issue tracking for small software teams. This is the starting point of the web client: routing,
        styling and the API foundation are in place; the application itself is built on top of it next.
      </p>

      <div className="mt-8 flex flex-wrap items-center gap-x-4 gap-y-3 rounded-lg border border-line bg-surface px-4 py-3">
        <span className="text-sm text-ink-muted">Backend</span>
        <BackendStatusBadge status={status} />
        <Link to="/health" className="text-sm font-medium text-accent underline-offset-4 hover:underline sm:ml-auto">
          Connectivity details
        </Link>
      </div>
    </section>
  )
}
