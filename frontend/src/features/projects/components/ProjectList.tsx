import type { Project } from '../../../api/projects'
import { Button } from '../../../components/ui/Button'

interface ProjectListProps {
  projects: Project[]
  /** ADMIN only: without it the rows have no Edit action. `button` is the Edit button that was used. */
  onEdit?: (project: Project, button: HTMLButtonElement) => void
}

/**
 * The projects in the backend's order. Rows are not links: there is no
 * project page yet. Below 28rem of list width the key moves above the name,
 * leaving the text the full width.
 */
export function ProjectList({ projects, onEdit }: ProjectListProps) {
  return (
    <div className="@container">
      <ul className="divide-y divide-line rounded-md border border-line bg-surface">
        {projects.map((project) => (
          <li key={project.id} className="flex items-start gap-3 px-4 py-3">
            <div className="flex min-w-0 flex-1 flex-col @md:flex-row @md:gap-3">
              <span className="shrink-0 font-mono text-xs whitespace-nowrap text-ink-subtle @md:min-w-14 @md:pt-0.5">
                {project.key}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-ink" title={project.name}>
                  {project.name}
                </p>
                {project.description && (
                  <p
                    className="mt-0.5 line-clamp-2 text-sm leading-6 break-words text-ink-muted"
                    title={project.description}
                  >
                    {project.description}
                  </p>
                )}
              </div>
            </div>
            {onEdit && (
              <Button
                variant="secondary"
                size="sm"
                aria-label={`Edit ${project.key}`}
                onClick={(event) => onEdit(project, event.currentTarget)}
                className="-my-1 shrink-0"
              >
                Edit
              </Button>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
