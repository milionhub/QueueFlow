import { CircleAlert, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import type { LoadFailure } from '../../api/errors'
import { Button } from './Button'

type HeadingLevel = 'h1' | 'h2' | 'h3'

interface EmptyStateProps {
  icon: LucideIcon
  title: string
  /** One or two sentences. */
  children?: ReactNode
  /** Only what the current role may do. */
  action?: ReactNode
  /** The page's h1 when the empty state is all there is (e.g. "not found"). */
  as?: HeadingLevel
  /** `plain` has no card of its own, for use inside one. */
  tone?: 'card' | 'plain'
  className?: string
}

/**
 * Nothing to show yet, or nothing matches: an icon in a soft circle, what
 * is going on, and the next step when there is one. Compact - never half a
 * screen of whitespace.
 */
export function EmptyState({
  icon: Icon,
  title,
  children,
  action,
  as: Heading = 'h2',
  tone = 'card',
  className = '',
}: EmptyStateProps) {
  return (
    <section
      className={`flex flex-col items-center px-6 py-10 text-center ${
        tone === 'card' ? 'rounded-lg border border-line bg-surface shadow-xs' : ''
      } ${className}`}
    >
      <span className="flex size-10 items-center justify-center rounded-full bg-canvas-strong text-ink-muted">
        <Icon aria-hidden="true" className="size-5" strokeWidth={1.75} />
      </span>
      <Heading className="mt-3 text-base font-semibold text-balance text-ink">{title}</Heading>
      {children && <div className="mt-1 max-w-md text-sm leading-6 text-pretty text-ink-muted">{children}</div>}
      {action && <div className="mt-4 flex flex-wrap justify-center gap-2">{action}</div>}
    </section>
  )
}

interface ErrorStateProps {
  /** What failed, as a sentence: "The dashboard could not be loaded." */
  message: string
  reason: LoadFailure
  onRetry: () => void
  /** `inline` for a section inside a page (comments, activity). */
  size?: 'page' | 'inline'
}

/** Data that could not be loaded: what failed, why in plain words, and Retry. */
export function ErrorState({ message, reason, onRetry, size = 'page' }: ErrorStateProps) {
  const { t } = useTranslation()
  const why = reason === 'network' ? t('load.serverUnreachable') : t('load.serverProblem')
  if (size === 'inline') {
    return (
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-danger/20 bg-danger/5 px-3 py-2.5">
        <p role="alert" className="flex min-w-0 flex-1 items-start gap-2 text-sm leading-5 text-danger">
          <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" strokeWidth={2} />
          <span>
            {message} {why} {t('load.pleaseTryAgain')}
          </span>
        </p>
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {t('actions.retry')}
        </Button>
      </div>
    )
  }
  return (
    <section className="flex max-w-lg flex-col items-start gap-4 rounded-lg border border-line bg-surface px-5 py-5 shadow-xs">
      <div role="alert" className="flex items-start gap-3">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-danger/10 text-danger">
          <CircleAlert aria-hidden="true" className="size-4" strokeWidth={2} />
        </span>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-ink">{message}</p>
          <p className="mt-0.5 text-sm leading-6 text-ink-muted">
            {why} {t('load.pleaseTryAgain')}
          </p>
        </div>
      </div>
      <Button variant="secondary" onClick={onRetry} className="ml-11">
        {t('actions.retry')}
      </Button>
    </section>
  )
}

/**
 * Data on screen that a refresh could not update: it stays, with a way to
 * try again.
 */
export function StaleNotice({
  children,
  onRefresh,
  label,
}: {
  children: ReactNode
  onRefresh: () => void
  /** The button's text; "Refresh" by default. */
  label?: string
}) {
  const { t } = useTranslation()
  return (
    <p className="flex flex-wrap items-center gap-x-2 text-sm text-ink-muted">
      <CircleAlert aria-hidden="true" className="size-4 shrink-0 text-warning" strokeWidth={2} />
      <span>{children}</span>
      <button
        type="button"
        onClick={onRefresh}
        className="rounded-sm font-medium text-accent underline-offset-4 hover:underline"
      >
        {label ?? t('actions.refresh')}
      </button>
    </p>
  )
}

/**
 * The loading placeholder of a section: announced at once for assistive
 * technology, drawn only after 150ms so fast loads do not flash.
 */
export function SkeletonFrame({
  label,
  children,
  className = '',
}: {
  label: string
  children: ReactNode
  className?: string
}) {
  return (
    <div aria-busy="true" className={className}>
      <span className="sr-only" role="status">
        {label}
      </span>
      <div aria-hidden="true" className="skeleton-delay">
        {children}
      </div>
    </div>
  )
}
