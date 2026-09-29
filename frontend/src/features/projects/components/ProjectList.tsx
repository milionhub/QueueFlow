import { ChevronRight, Pencil } from 'lucide-react'
import { Link } from 'react-router'

import type { Project } from '../../../api/projects'
import { IconButton } from '../../../components/ui/IconButton'
import { KeyBadge } from '../../../components/ui/PageHeader'
import { projectPath } from '../../../routes/paths'
import { ProjectMark } from './ProjectMark'

interface ProjectListProps {
  projects: Project[]
  /** ADMIN only: without it the rows have no Edit action. `button` is the Edit button that was used. */
  onEdit?: (project: Project, button: HTMLButtonElement) => void
}

/**
 * The projects in the backend's order. The whole row is the link to the
 * project's page (its name is the link's text); Edit sits above the link,
 * as its own control. Below 28rem of list width the key moves above the
 * name, leaving the text the full width.
 */
export function ProjectList({ projects, onEdit }: ProjectListProps) {
  return (
    <div className="@container">
      <ul className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface shadow-xs">
        {projects.map((project) => (
          <li
            key={project.id}
            className="group relative flex min-h-16 items-center gap-3 px-4 py-3 transition-colors duration-150 hover:bg-accent-subtle/40 before:pointer-events-none before:absolute before:inset-y-0 before:left-0 before:w-0.5 before:bg-accent before:opacity-0 before:transition-opacity hover:before:opacity-100 focus-within:before:opacity-100"
          >
            <ProjectMark projectId={project.id} projectKey={project.key} />
            <div className="flex min-w-0 flex-1 flex-col gap-1 @md:flex-row @md:items-center @md:gap-3">
              <span className="flex shrink-0 @md:w-24">
                <KeyBadge>{project.key}</KeyBadge>
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium" title={project.name}>
                  <Link
                    to={projectPath(project.key)}
                    className="text-ink outline-none after:absolute after:inset-0 after:content-[''] group-hover:text-accent-strong focus-visible:after:rounded-sm keyboard:focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-accent"
                  >
                    {project.name}
                  </Link>
                </p>
                {project.description && (
                  <p className="mt-0.5 truncate text-sm text-ink-muted" title={project.description}>
                    {project.description}
                  </p>
                )}
              </div>
            </div>
            {onEdit && (
              <IconButton
                icon={Pencil}
                label={`Edit ${project.key}`}
                onClick={(event) => onEdit(project, event.currentTarget)}
                className="relative z-10 shrink-0"
              />
            )}
            <ChevronRight
              aria-hidden="true"
              className="size-4 shrink-0 text-ink-subtle transition-transform duration-150 group-hover:translate-x-0.5 motion-reduce:transform-none"
              strokeWidth={2}
            />
          </li>
        ))}
      </ul>
    </div>
  )
}
