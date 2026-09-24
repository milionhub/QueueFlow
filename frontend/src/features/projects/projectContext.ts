import { createContext, useContext } from 'react'

import type { Member } from '../../api/members'
import type { Project } from '../../api/projects'

/** What every page under /app/projects/:projectKey shares, loaded once by ProjectLayout. */
export interface ProjectContextValue {
  project: Project
  /** The workspace's members, in the backend's order: the possible assignees. */
  members: Member[]
  /** A member's name by id: "Unknown user" if they are not in the list loaded. */
  memberName: (userId: string) => string
}

export const ProjectContext = createContext<ProjectContextValue | null>(null)

export function useProjectContext(): ProjectContextValue {
  const context = useContext(ProjectContext)
  if (!context) {
    throw new Error('useProjectContext must be used inside <ProjectLayout>')
  }
  return context
}
