import { ChevronLeft, ChevronRight, Menu } from 'lucide-react'
import { Fragment, type Ref } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { QueueFlowLogo } from '../QueueFlowMark'

export interface Crumb {
  label: string
  to: string
}

interface AppHeaderProps {
  /** The pages above the current one, outermost first; empty on a top-level page. */
  trail: Crumb[]
  navigationOpen: boolean
  onOpenNavigation: () => void
  menuButtonRef: Ref<HTMLButtonElement>
  navigationId: string
}

/**
 * The bar above every application page. On large screens it holds the
 * breadcrumbs, and is left out on top-level pages, where there are none;
 * the page's own heading names the page. On smaller screens it is always
 * there, with the navigation button and a link back to the parent page.
 */
export function AppHeader({ trail, navigationOpen, onOpenNavigation, menuButtonRef, navigationId }: AppHeaderProps) {
  const { t } = useTranslation('shell')
  const parent = trail.at(-1)
  return (
    <header
      className={`sticky top-0 z-30 flex shrink-0 items-center gap-2 border-b border-line bg-surface px-4 pt-[env(safe-area-inset-top)] sm:px-6 lg:px-8 ${
        trail.length === 0 ? 'lg:hidden' : ''
      }`}
    >
      <div className="flex h-14 min-w-0 flex-1 items-center gap-2 lg:h-12">
        <button
          ref={menuButtonRef}
          type="button"
          onClick={onOpenNavigation}
          aria-label={t('openNavigation')}
          aria-expanded={navigationOpen}
          aria-controls={navigationId}
          className="press -ml-2 inline-flex size-10 shrink-0 items-center justify-center rounded-md text-ink-muted transition-colors hover:bg-canvas-strong hover:text-ink lg:hidden"
        >
          <Menu aria-hidden="true" className="size-5" strokeWidth={2} />
        </button>

        {parent ? (
          <>
            {/* Phones and tablets: back to the page above. */}
            <Link
              to={parent.to}
              className="press -ml-1 inline-flex h-10 min-w-0 items-center gap-1 rounded-md pr-2 pl-1 text-sm font-medium text-ink-muted transition-colors hover:bg-canvas-strong hover:text-ink lg:hidden"
            >
              <ChevronLeft aria-hidden="true" className="size-4 shrink-0" strokeWidth={2} />
              <span className="truncate">{parent.label}</span>
              <span className="sr-only">{t('back')}</span>
            </Link>
            <nav aria-label={t('breadcrumb')} className="hidden min-w-0 lg:block">
              <ol className="flex min-w-0 items-center gap-1 text-[13px]">
                {trail.map((crumb, index) => (
                  <Fragment key={crumb.to}>
                    {index > 0 && (
                      <li aria-hidden="true" className="flex shrink-0 text-ink-subtle">
                        <ChevronRight className="size-3.5" strokeWidth={2} />
                      </li>
                    )}
                    <li className="min-w-0">
                      <Link
                        to={crumb.to}
                        className="block truncate rounded px-1.5 py-1 font-medium text-ink-muted transition-colors hover:bg-canvas-strong hover:text-ink"
                      >
                        {crumb.label}
                      </Link>
                    </li>
                  </Fragment>
                ))}
              </ol>
            </nav>
          </>
        ) : (
          <span className="lg:hidden">
            <QueueFlowLogo size="sm" />
          </span>
        )}
      </div>
    </header>
  )
}
