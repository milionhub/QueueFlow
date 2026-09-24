import { Columns3, List, type LucideIcon } from 'lucide-react'
import { NavLink, useLocation } from 'react-router'

import { projectBoardPath, projectPath } from '../../../routes/paths'
import { sharedFilterSearch, type TicketFilters } from '../../tickets/ticketFilters'

const VIEWS: { label: string; icon: LucideIcon; to: (projectKey: string) => string; end: boolean }[] = [
  { label: 'List', icon: List, to: projectPath, end: true },
  { label: 'Board', icon: Columns3, to: projectBoardPath, end: false },
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
  const location = useLocation()
  const shared = filters ? sharedFilterSearch(filters) : ''
  return (
    <nav aria-label="Project views">
      <ul className="inline-flex rounded-md border border-line bg-canvas p-0.5">
        {VIEWS.map(({ label, icon: Icon, to, end }) => {
          const pathname = to(projectKey)
          return (
            <li key={label}>
              <NavLink
                to={{ pathname, search: pathname === location.pathname ? location.search : shared }}
                end={end}
                className={({ isActive }) =>
                  `inline-flex h-8 items-center gap-1.5 rounded px-3 text-sm ${
                    isActive
                      ? 'bg-surface font-semibold text-ink shadow-xs ring-1 ring-line'
                      : 'font-medium text-ink-muted hover:text-ink'
                  }`
                }
              >
                <Icon aria-hidden="true" className="size-4 shrink-0" strokeWidth={2} />
                {label}
              </NavLink>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
