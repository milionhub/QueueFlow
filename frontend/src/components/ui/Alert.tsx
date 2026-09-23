import type { ReactNode } from 'react'

type AlertTone = 'error' | 'info'

const TONE_CLASSES: Record<AlertTone, string> = {
  error: 'border-danger/25 bg-danger/5 text-danger',
  info: 'border-accent/25 bg-accent/5 text-ink',
}

/**
 * A message box above a form. Errors use role="alert" so they are announced
 * as soon as they appear; the text itself always states the problem.
 */
export function Alert({ tone, children }: { tone: AlertTone; children: ReactNode }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-md border px-3 py-2.5 text-sm leading-5 ${TONE_CLASSES[tone]}`}
    >
      {children}
    </div>
  )
}
