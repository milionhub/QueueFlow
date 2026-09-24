import { Link } from 'react-router'

import type { DashboardProject } from '../../../api/dashboard'
import { projectPath } from '../../../routes/paths'

/** Every project with its ticket counts; the name links to the project's page. */
export function ProjectSummaryList({ projects }: { projects: DashboardProject[] }) {
  return (
    <ul className="divide-y divide-line rounded-md border border-line bg-surface">
      {projects.map((project) => (
        <li key={project.id} className="flex items-baseline gap-3 px-4 py-2.5">
          <span className="min-w-10 shrink-0 font-mono text-xs whitespace-nowrap text-ink-subtle">{project.key}</span>
          <span className="min-w-0 flex-1 truncate text-sm" title={project.name}>
            <Link to={projectPath(project.key)} className="text-ink underline-offset-4 hover:text-accent hover:underline">
              {project.name}
            </Link>
          </span>
          <span className="shrink-0 text-xs whitespace-nowrap text-ink-muted tabular-nums">
            {project.ticketCount === 0 ? (
              <span className="text-ink-subtle">No tickets</span>
            ) : (
              <>
                <span className="text-ink">{project.openTicketCount}</span> open / {project.ticketCount}
                <span className="sr-only"> tickets in total</span>
              </>
            )}
          </span>
        </li>
      ))}
    </ul>
  )
}
