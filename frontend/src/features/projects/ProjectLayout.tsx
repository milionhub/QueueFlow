import { useMemo } from 'react'
import { Link, Navigate, Outlet, useLocation, useParams } from 'react-router'

import { isNotFound } from '../../api/errors'
import { listMembers } from '../../api/members'
import { getProjectByKey } from '../../api/projects'
import { LoadError } from '../../components/ui/LoadError'
import { usePageTitle } from '../../hooks/usePageTitle'
import { useResource } from '../../lib/useResource'
import { PROJECTS_PATH } from '../../routes/paths'
import type { CurrentUser } from '../auth/types'
import { useAuth } from '../auth/useAuth'
import { ProjectContext, type ProjectContextValue } from './projectContext'

const BLOCK = 'rounded bg-line motion-safe:animate-pulse'

/**
 * /app/projects/:projectKey and everything below it. Loads the project by
 * its key and the workspace's members once, for all the pages inside:
 * moving between a project's ticket list and its tickets reuses both.
 * A key typed in another case is corrected in the address. The pages set
 * their own titles (a parent's would overwrite its child's: React runs the
 * child's effects first).
 */
export function ProjectLayout() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  return <ProjectContextLoader user={user} />
}

function ProjectContextLoader({ user }: { user: CurrentUser }) {
  const { authorizedRequest } = useAuth()
  const { projectKey = '' } = useParams()
  const location = useLocation()

  // Keyed by the key as the backend normalizes it, so correcting the
  // address's case does not load the project again.
  const normalizedKey = projectKey.trim().toUpperCase()
  const projectResource = useResource(`${user.workspaceId}:${normalizedKey}`, (signal) =>
    getProjectByKey(authorizedRequest, projectKey, signal),
  )
  const membersResource = useResource(user.workspaceId, (signal) =>
    listMembers(authorizedRequest, user.workspaceId, signal),
  )

  const projectState = projectResource.state
  const membersState = membersResource.state
  const project = projectState.status === 'ready' ? projectState.data : null
  const members = membersState.status === 'ready' ? membersState.data : null

  const context = useMemo<ProjectContextValue | null>(() => {
    if (!project || !members) {
      return null
    }
    const names = new Map(members.map((member) => [member.id, member.name]))
    return { project, members, memberName: (userId) => names.get(userId) ?? 'Unknown user' }
  }, [project, members])

  if (projectState.status === 'error') {
    if (isNotFound(projectState.error)) {
      return <ProjectNotFound />
    }
    return (
      <LoadError message="The project could not be loaded." reason={projectState.reason} onRetry={projectResource.retry} />
    )
  }
  if (membersState.status === 'error') {
    return (
      <LoadError message="The project could not be loaded." reason={membersState.reason} onRetry={membersResource.retry} />
    )
  }
  if (!context) {
    return <ProjectSkeleton />
  }

  if (context.project.key !== projectKey) {
    const segments = location.pathname.split('/')
    // ['', 'app', 'projects', <key>, ...rest]
    segments[3] = encodeURIComponent(context.project.key)
    return <Navigate to={{ pathname: segments.join('/'), search: location.search, hash: location.hash }} replace />
  }

  return (
    <ProjectContext value={context}>
      <Outlet />
    </ProjectContext>
  )
}

/** Unknown key, or another workspace's project: the same answer, never a hint that it exists elsewhere. */
function ProjectNotFound() {
  usePageTitle('Project not found')
  return (
    <section aria-labelledby="project-not-found" className="max-w-lg rounded-md border border-line bg-surface px-5 py-4">
      <h2 id="project-not-found" className="text-sm font-semibold text-ink">
        Project not found
      </h2>
      <p className="mt-1.5 text-sm leading-6 text-ink-muted">
        There is no project with this key in your workspace.{' '}
        <Link to={PROJECTS_PATH} className="font-medium text-accent underline-offset-4 hover:underline">
          Back to Projects
        </Link>
      </p>
    </section>
  )
}

function ProjectSkeleton() {
  return (
    <div aria-busy="true" className="max-w-6xl">
      <span className="sr-only" role="status">
        Loading project…
      </span>
      <div aria-hidden="true" className="flex flex-col gap-4">
        <div className={`h-4 w-56 max-w-full ${BLOCK}`} />
        <div className="divide-y divide-line rounded-md border border-line bg-surface">
          {[60, 45, 52].map((width) => (
            <div key={width} className="flex items-center gap-3 px-4 py-3">
              <div className={`h-3 w-14 shrink-0 ${BLOCK}`} />
              <div className={`h-3 ${BLOCK}`} style={{ width: `${width}%` }} />
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
