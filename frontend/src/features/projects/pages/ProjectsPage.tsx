import { useEffect, useId, useRef, useState, type MouseEvent } from 'react'

import type { Project } from '../../../api/projects'
import { Button } from '../../../components/ui/Button'
import { LoadError } from '../../../components/ui/LoadError'
import type { CurrentUser } from '../../auth/types'
import { useAuth } from '../../auth/useAuth'
import { WorkspaceName } from '../../workspace/WorkspaceName'
import { ProjectFormDialog, type ProjectFormMode } from '../components/ProjectFormDialog'
import { ProjectList } from '../components/ProjectList'
import { ProjectsEmptyState } from '../components/ProjectsEmptyState'
import { ProjectsSkeleton } from '../components/ProjectsSkeleton'
import { useProjects } from '../useProjects'

/**
 * /app/projects: the workspace's projects. Everyone can read them; an
 * ADMIN also creates and edits them (the backend enforces the same rule).
 * After a save the list is reloaded, so the order stays the backend's.
 */
export function ProjectsPage() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  return <Projects user={user} />
}

function Projects({ user }: { user: CurrentUser }) {
  const { state, retry, reload } = useProjects(user.workspaceId)
  const isAdmin = user.role === 'ADMIN'
  const [dialog, setDialog] = useState<{ mode: ProjectFormMode; opener: HTMLElement | null } | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const newProjectId = useId()

  // Creating the first project replaces the empty state (and its button)
  // with the list: once that reload lands, focus moves to the page's own
  // "New project" button instead of being lost.
  const focusNewProjectAfterReload = useRef(false)
  useEffect(() => {
    if (!focusNewProjectAfterReload.current || state.status !== 'ready' || state.refreshing) {
      return
    }
    focusNewProjectAfterReload.current = false
    if (!document.activeElement || document.activeElement === document.body) {
      document.getElementById(newProjectId)?.focus()
    }
  }, [state, newProjectId])

  function handleSaved(project: Project) {
    const created = dialog?.mode.kind === 'create'
    setDialog(null)
    setAnnouncement(`Project ${project.key} ${created ? 'created' : 'updated'}.`)
    focusNewProjectAfterReload.current = created
    reload()
  }

  const openCreate = isAdmin
    ? (event: MouseEvent<HTMLButtonElement>) => setDialog({ mode: { kind: 'create' }, opener: event.currentTarget })
    : undefined

  let content
  switch (state.status) {
    case 'loading':
      content = <ProjectsSkeleton />
      break
    case 'error':
      content = <LoadError message="The projects could not be loaded." reason={state.reason} onRetry={retry} />
      break
    case 'ready':
      content =
        state.projects.length === 0 ? (
          <ProjectsEmptyState onCreate={openCreate} />
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
              <p className="text-sm text-ink-muted">
                <WorkspaceName />
                {' · '}
                {state.projects.length} {state.projects.length === 1 ? 'project' : 'projects'}
              </p>
              {openCreate && (
                <Button id={newProjectId} onClick={openCreate}>
                  New project
                </Button>
              )}
            </div>
            {state.refreshFailed && (
              <p className="text-sm text-ink-muted">
                The list could not be refreshed and may be out of date.{' '}
                <button
                  type="button"
                  onClick={reload}
                  className="font-medium text-accent underline-offset-4 hover:underline"
                >
                  Refresh
                </button>
              </p>
            )}
            <ProjectList
              projects={state.projects}
              onEdit={isAdmin ? (project, opener) => setDialog({ mode: { kind: 'edit', project }, opener }) : undefined}
            />
          </div>
        )
      break
  }

  return (
    <div className="max-w-4xl">
      {content}
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {isAdmin && dialog && (
        <ProjectFormDialog
          mode={dialog.mode}
          onClose={() => setDialog(null)}
          onSaved={handleSaved}
          onProjectGone={reload}
          returnFocus={dialog.opener}
          fallbackFocus={() => document.getElementById(newProjectId)}
        />
      )}
    </div>
  )
}
