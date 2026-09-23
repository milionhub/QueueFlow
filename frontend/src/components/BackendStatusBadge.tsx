import type { BackendStatus } from '../hooks/useBackendHealth'

const LABELS: Record<BackendStatus, string> = {
  checking: 'Checking…',
  connected: 'Connected',
  unavailable: 'Unavailable',
}

const DOT_CLASSES: Record<BackendStatus, string> = {
  checking: 'bg-ink-subtle',
  connected: 'bg-success',
  unavailable: 'bg-danger',
}

/** Backend connectivity as text plus a colored dot (the text carries the meaning). */
export function BackendStatusBadge({ status }: { status: BackendStatus }) {
  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-line bg-surface px-2.5 py-1 text-xs font-medium text-ink">
      <span aria-hidden="true" className={`size-2 rounded-full ${DOT_CLASSES[status]}`} />
      {LABELS[status]}
    </span>
  )
}
