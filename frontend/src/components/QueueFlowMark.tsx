/** The QueueFlow logo mark (same drawing as the favicon). Decorative: pair it with the name. */
export function QueueFlowMark({ className = 'size-7' }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={className}>
      <rect width="32" height="32" rx="7" fill="var(--color-accent)" />
      <rect x="8" y="9" width="16" height="3" rx="1.5" fill="#fff" />
      <rect x="8" y="14.5" width="11" height="3" rx="1.5" fill="#fff" opacity=".85" />
      <rect x="8" y="20" width="6" height="3" rx="1.5" fill="#fff" opacity=".7" />
    </svg>
  )
}

/** Mark plus name. */
export function QueueFlowLogo() {
  return (
    <span className="inline-flex items-center gap-2.5">
      <QueueFlowMark />
      <span className="text-lg font-semibold tracking-tight text-ink">QueueFlow</span>
    </span>
  )
}
