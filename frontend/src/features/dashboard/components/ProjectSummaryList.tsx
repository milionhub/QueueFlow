import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import type { DashboardProject } from '../../../api/dashboard'
import { KeyBadge } from '../../../components/ui/PageHeader'
import { projectPath } from '../../../routes/paths'
import { ProjectMark } from '../../projects/components/ProjectMark'

/**
 * Every project with its ticket counts and how much of it is done (the
 * backend's own counts: done = all - open). The whole row links to the
 * project's page.
 */
export function ProjectSummaryList({ projects }: { projects: DashboardProject[] }) {
  const { t } = useTranslation('dashboard')
  return (
    <ul className="divide-y divide-line">
      {projects.map((project) => {
        const done = project.ticketCount - project.openTicketCount
        const percent = project.ticketCount === 0 ? 0 : Math.round((done / project.ticketCount) * 100)
        return (
          <li
            key={project.id}
            className="group relative px-4 py-3 transition-colors duration-150 hover:bg-accent-subtle/40 before:pointer-events-none before:absolute before:inset-y-0 before:left-0 before:w-0.5 before:bg-accent before:opacity-0 before:transition-opacity hover:before:opacity-100 focus-within:before:opacity-100"
          >
            <div className="flex min-w-0 items-center gap-2.5">
              <ProjectMark projectId={project.id} projectKey={project.key} size="sm" />
              <KeyBadge>{project.key}</KeyBadge>
              <Link
                to={projectPath(project.key)}
                title={project.name}
                className="min-w-0 flex-1 truncate text-sm font-medium text-ink outline-none after:absolute after:inset-0 after:content-[''] group-hover:text-accent-strong focus-visible:after:rounded-sm keyboard:focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-accent"
              >
                {project.name}
              </Link>
              <span className="shrink-0 text-xs whitespace-nowrap text-ink-muted tabular-nums">
                {project.ticketCount === 0 ? (
                  <span className="text-ink-subtle">{t('projects.noTickets')}</span>
                ) : (
                  <>
                    <span className="font-medium text-ink">{project.openTicketCount}</span>{' '}
                    {t('projects.open', { count: project.openTicketCount })}
                  </>
                )}
              </span>
            </div>
            {project.ticketCount > 0 && (
              <div className="mt-2 flex items-center gap-2.5 pl-9.5">
                <div aria-hidden="true" className="h-1 flex-1 overflow-hidden rounded-full bg-canvas-strong">
                  <div className="h-full rounded-full bg-success" style={{ width: `${percent}%` }} />
                </div>
                <span className="shrink-0 text-xs text-ink-subtle tabular-nums">
                  {t('projects.done', { done, total: project.ticketCount })}
                  <span className="sr-only"> ({percent}%)</span>
                </span>
              </div>
            )}
          </li>
        )
      })}
    </ul>
  )
}
