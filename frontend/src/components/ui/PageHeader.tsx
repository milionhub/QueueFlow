import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: ReactNode
  /** Above the title, small: e.g. a project's key. */
  eyebrow?: ReactNode
  /** Under the title: one or two lines of context. */
  description?: ReactNode
  /** On the right from `sm`; under the text on phones. */
  actions?: ReactNode
  /** Next to the title, e.g. an edit button. */
  titleAccessory?: ReactNode
  /** An id for the h1, when something else refers to it. */
  titleId?: string
  /** Before the text, e.g. a project's mark. */
  leading?: ReactNode
}

/**
 * The top of every application page: the page's one h1 (20px, 24px from
 * `sm`), optional context, and its main actions.
 */
export function PageHeader({
  title,
  eyebrow,
  description,
  actions,
  titleAccessory,
  titleId,
  leading,
}: PageHeaderProps) {
  return (
    <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
      <div className="flex min-w-0 flex-1 items-start gap-3.5">
        {leading}
        <div className="min-w-0 flex-1">
          {eyebrow && <div className="mb-1.5 flex min-w-0 flex-wrap items-center gap-2">{eyebrow}</div>}
          <div className="flex min-w-0 items-start gap-2">
            <h1
              id={titleId}
              tabIndex={-1}
              className="min-w-0 text-xl leading-7 font-semibold tracking-tight break-words text-ink outline-none sm:text-2xl sm:leading-8"
            >
              {title}
            </h1>
            {titleAccessory}
          </div>
          {description && <div className="mt-1 text-sm leading-6 text-ink-muted">{description}</div>}
        </div>
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

/** A project's key (or a ticket's), as a compact technical identifier. */
export function KeyBadge({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`inline-flex h-5 shrink-0 items-center rounded border border-line bg-canvas px-1.5 font-mono text-[11px] leading-none font-medium tracking-wide whitespace-nowrap text-ink-muted ${className}`}
    >
      {children}
    </span>
  )
}
