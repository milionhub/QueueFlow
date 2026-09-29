import { CircleAlert, Info } from 'lucide-react'
import type { ReactNode } from 'react'

type AlertTone = 'error' | 'info'

const TONE_CLASSES: Record<AlertTone, string> = {
  error: 'border-danger/20 bg-danger/5 text-danger',
  info: 'border-accent/20 bg-accent/5 text-ink',
}

/**
 * A message box above a form. Errors use role="alert" so they are announced
 * as soon as they appear; the text itself always states the problem. It
 * fades in, so a new message is noticed without anything shaking.
 */
export function Alert({
  tone,
  announce = true,
  children,
}: {
  tone: AlertTone
  /** False when a live region around it already announces it. */
  announce?: boolean
  children: ReactNode
}) {
  const Icon = tone === 'error' ? CircleAlert : Info
  return (
    <div
      role={announce ? (tone === 'error' ? 'alert' : 'status') : undefined}
      className={`flex animate-fade-in items-start gap-2.5 rounded-lg border px-3 py-2.5 text-sm leading-5 ${TONE_CLASSES[tone]}`}
    >
      <Icon
        aria-hidden="true"
        className={`mt-0.5 size-4 shrink-0 ${tone === 'error' ? '' : 'text-accent'}`}
        strokeWidth={2}
      />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
}
