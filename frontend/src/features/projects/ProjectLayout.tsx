import { FolderSearch } from 'lucide-react'
import { useMemo } from 'react'
import { Link, Navigate, Outlet, useLocation, useParams } from 'react-router'

import { isNotFound } from '../../api/errors'
import { listMembers } from '../../api/members'
import { getProjectByKey } from '../../api/projects'
import { buttonLinkClasses } from '../../components/ui/buttonStyles'
import { LoadError } from '../../components/ui/LoadError'
import { EmptyState, SkeletonFrame } from '../../components/ui/States'
import { usePageTitle } from '../../hooks/usePageTitle'
import { useShellProject } from '../../hooks/useShellProject'
import { useResource } from '../../lib/useResource'
import { PROJECTS_PATH } from '../../routes/paths'
import type { CurrentUser } from '../auth/types'
import { useAuth } from '../auth/useAuth'
import { ProjectContext, type ProjectContextValue } from './projectContext'

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

  // The breadcrumbs show the project's name as soon as it is known.
  useShellProject(project)

  const context = useMemo<ProjectContextValue | null>(() => {
    if (!project || !members) {
      return null
    }
    const names = new Map(members.map((member) => [member.id, member.name]))
    // An id from this workspace's tickets that is not in the member list belongs to someone an
    // ADMIN has removed: the tickets they created and history they appear in stay.
    return { project, members, memberName: (userId) => names.get(userId) ?? 'Former member' }
  }, [project, members])

  if (projectState.status === 'error') {
    if (isNotFound(projectState.error)) {
      return <ProjectNotFound />
    }
    return (
      <LoadError
        message="The project could not be loaded."
        reason={projectState.reason}
        onRetry={projectResource.retry}
      />
    )
  }
  if (membersState.status === 'error') {
    return (
      <LoadError
        message="The project could not be loaded."
        reason={membersState.reason}
        onRetry={membersResource.retry}
      />
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
    <EmptyState
      as="h1"
      icon={FolderSearch}
      title="Project not found"
      action={
        <Link to={PROJECTS_PATH} className={buttonLinkClasses('secondary')}>
          Back to Projects
        </Link>
      }
      className="max-w-xl"
    >
      There is no project with this key in your workspace.
    </EmptyState>
  )
}

/**
 * A project page loading: its header and a few rows. Also shown by the
 * router while a lazily loaded project page (the board) arrives on a
 * direct visit or a refresh.
 */
export function ProjectSkeleton() {
  return (
    <SkeletonFrame label="Loading project…" className="max-w-7xl">
      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-2.5">
          <div className="skeleton h-4 w-12" />
          <div className="skeleton h-7 w-56 max-w-full" />
        </div>
        <div className="skeleton h-9 w-full max-w-md" />
        <div className="divide-y divide-line rounded-lg border border-line bg-surface shadow-xs">
          {[60, 45, 52, 38].map((width) => (
            <div key={width} className="flex h-12 items-center gap-3 px-4">
              <div className="skeleton h-3 w-14 shrink-0" />
              <div className="skeleton h-3" style={{ width: `${width}%` }} />
            </div>
          ))}
        </div>
      </div>
    </SkeletonFrame>
  )
}
