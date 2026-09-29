import type { ReactNode } from 'react'

import type { Project } from '../../../api/projects'
import { KeyBadge, PageHeader } from '../../../components/ui/PageHeader'
import type { TicketFilters } from '../../tickets/ticketFilters'
import { ProjectMark } from './ProjectMark'
import { ProjectViewNav } from './ProjectViewNav'

interface ProjectPageHeaderProps {
  project: Project
  /** The current view's filters, carried to the other view. */
  filters?: TicketFilters
  /** e.g. "New ticket", after the List | Board switch. */
  action?: ReactNode
}

/**
 * The top of a project's list and board: its mark (the key's initial on a
 * tint that follows the project's id), key, name (the page's h1) and
 * description, the List | Board switch and the page's main action.
 */
export function ProjectPageHeader({ project, filters, action }: ProjectPageHeaderProps) {
  return (
    <PageHeader
      leading={
        <span className="mt-0.5 hidden sm:block">
          <ProjectMark projectId={project.id} projectKey={project.key} size="lg" />
        </span>
      }
      eyebrow={<KeyBadge>{project.key}</KeyBadge>}
      title={project.name}
      description={project.description && <p className="line-clamp-2 max-w-3xl break-words">{project.description}</p>}
      actions={
        <>
          <ProjectViewNav projectKey={project.key} filters={filters} />
          {action}
        </>
      }
    />
  )
}
