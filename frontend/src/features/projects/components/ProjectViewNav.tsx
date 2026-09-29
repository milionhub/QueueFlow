import { Columns3, List, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink, useLocation } from 'react-router'

import { projectBoardPath, projectPath } from '../../../routes/paths'
import { sharedFilterSearch, type TicketFilters } from '../../tickets/ticketFilters'

/** Fetches the board's code ahead of a likely visit (hover or focus of its tab). Loaded once. */
let boardPrefetched = false
function prefetchBoard() {
  if (!boardPrefetched) {
    boardPrefetched = true
    void import('../../board/pages/ProjectBoardPage').catch(() => {
      boardPrefetched = false
    })
  }
}

const VIEWS: {
  /** The view's name: a message in projects.views. */
  labelKey: 'list' | 'board'
  icon: LucideIcon
  to: (projectKey: string) => string
  end: boolean
  prefetch?: () => void
}[] = [
  { labelKey: 'list', icon: List, to: projectPath, end: true },
  { labelKey: 'board', icon: Columns3, to: projectBoardPath, end: false, prefetch: prefetchBoard },
]

/**
 * The project's two views of the same tickets, as links. The current one
 * gets aria-current="page" (NavLink sets it) and a raised, bold tab - not
 * colour alone. Given the current view's filters, the links keep the ones
 * both views have - search, priority, assignee, label - so switching view
 * keeps looking at the same tickets; status is never carried (the board's
 * columns are the statuses). The current view's own link keeps the
 * address as it is.
 */
export function ProjectViewNav({ projectKey, filters }: { projectKey: string; filters?: TicketFilters }) {
  const { t } = useTranslation('projects')
  const location = useLocation()
  const shared = filters ? sharedFilterSearch(filters) : ''
  return (
    <nav aria-label={t('views.label')}>
      <ul className="inline-flex rounded-lg bg-canvas-strong p-0.5">
        {VIEWS.map(({ labelKey, icon: Icon, to, end, prefetch }) => {
          const pathname = to(projectKey)
          return (
            <li key={labelKey}>
              <NavLink
                to={{ pathname, search: pathname === location.pathname ? location.search : shared }}
                end={end}
                onPointerEnter={prefetch}
                onFocus={prefetch}
                className={({ isActive }) =>
                  `press inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-sm transition-[color,background-color,box-shadow] duration-150 pointer-coarse:h-9 ${
                    isActive
                      ? 'bg-surface font-semibold text-ink shadow-xs ring-1 ring-line'
                      : 'font-medium text-ink-muted hover:text-ink'
                  }`
                }
              >
                <Icon aria-hidden="true" className="size-4 shrink-0" strokeWidth={2} />
                {t(`views.${labelKey}`)}
              </NavLink>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
