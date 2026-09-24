import type { DashboardProject } from '../../../api/dashboard'

/** Every project with its ticket counts. Not links: the Projects section is not built yet. */
export function ProjectSummaryList({ projects }: { projects: DashboardProject[] }) {
  return (
    <ul className="divide-y divide-line rounded-md border border-line bg-surface">
      {projects.map((project) => (
        <li key={project.id} className="flex items-baseline gap-3 px-4 py-2.5">
          <span className="min-w-10 shrink-0 font-mono text-xs whitespace-nowrap text-ink-subtle">{project.key}</span>
          <span className="min-w-0 flex-1 truncate text-sm text-ink" title={project.name}>
            {project.name}
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
