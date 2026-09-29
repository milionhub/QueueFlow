import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

/** A section's hue: a small icon disc and its count, never the whole card. */
export type SectionTone = 'accent' | 'info' | 'review' | 'success'

const TONES: Record<SectionTone, string> = {
  accent: 'bg-accent-subtle text-accent',
  info: 'bg-info-subtle text-info',
  review: 'bg-status-review-subtle text-status-review',
  success: 'bg-success-subtle text-success-text',
}

const HEADER_TINTS: Record<SectionTone, string> = {
  accent: 'bg-accent-subtle/45',
  info: 'bg-info-subtle/45',
  review: 'bg-status-review-subtle/45',
  success: 'bg-success-subtle/45',
}

interface SectionCardProps {
  id: string
  title: string
  /** The section's icon, in a disc of its tone. */
  icon: LucideIcon
  tone: SectionTone
  /** A count next to the title, e.g. "7 open". */
  count?: string
  /** At the far end of the title row: a link or a short note. */
  aside?: ReactNode
  /** Under the content, e.g. "Showing 10 of 12". */
  footer?: ReactNode
  className?: string
  /** The page's primary section: its title row gets a faint tint of its tone. */
  primary?: boolean
  children: ReactNode
}

/** One dashboard section: a card with a title row and its content. */
export function SectionCard({
  id,
  title,
  icon: Icon,
  tone,
  count,
  aside,
  footer,
  className = '',
  primary = false,
  children,
}: SectionCardProps) {
  return (
    <section
      aria-labelledby={id}
      className={`min-w-0 overflow-hidden rounded-lg border border-line bg-surface shadow-xs ${className}`}
    >
      <div
        className={`flex min-h-12 flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-line px-4 py-2.5 ${
          primary ? HEADER_TINTS[tone] : ''
        }`}
      >
        <h2 id={id} className="flex items-center gap-2.5 text-sm font-semibold text-ink">
          <span aria-hidden="true" className={`flex size-7 items-center justify-center rounded-md ${TONES[tone]}`}>
            <Icon className="size-4" strokeWidth={2} />
          </span>
          {title}
          {count && (
            <span className={`rounded-full px-2 text-xs leading-5 font-medium tabular-nums ${TONES[tone]}`}>
              {count}
            </span>
          )}
        </h2>
        {aside && <div className="text-xs text-ink-muted">{aside}</div>}
      </div>
      {children}
      {footer && <div className="border-t border-line px-4 py-2.5 text-xs text-ink-muted">{footer}</div>}
    </section>
  )
}
