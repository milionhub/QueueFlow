import { Plus } from 'lucide-react'
import { useEffect, useId, useRef, useState, type MouseEvent } from 'react'

import type { Project } from '../../../api/projects'
import { Button } from '../../../components/ui/Button'
import { LoadError } from '../../../components/ui/LoadError'
import { PageHeader } from '../../../components/ui/PageHeader'
import { StaleNotice } from '../../../components/ui/States'
import { useToast } from '../../../components/ui/toastContext'
import { projectPath } from '../../../routes/paths'
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
 * After a save the list is reloaded, so the order stays the backend's, and
 * a toast confirms it - the dialog that did it has closed.
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
  const toast = useToast()
  const isAdmin = user.role === 'ADMIN'
  const [dialog, setDialog] = useState<{ mode: ProjectFormMode; opener: HTMLElement | null } | null>(null)
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
    toast.show({
      message: `Project ${project.key} ${created ? 'created' : 'updated'}.`,
      action: { label: 'Open', to: projectPath(project.key) },
    })
    focusNewProjectAfterReload.current = created
    reload()
  }

  const openCreate = isAdmin
    ? (event: MouseEvent<HTMLButtonElement>) => setDialog({ mode: { kind: 'create' }, opener: event.currentTarget })
    : undefined

  const count = state.status === 'ready' ? state.projects.length : null

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
          <div className="flex flex-col gap-3">
            {state.refreshFailed && (
              <StaleNotice onRefresh={reload}>The list could not be refreshed and may be out of date.</StaleNotice>
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
    <div className="flex max-w-4xl flex-col gap-6">
      <PageHeader
        title="Projects"
        description={
          <>
            <WorkspaceName />
            {count !== null && (
              <>
                {' · '}
                {count} {count === 1 ? 'project' : 'projects'}
              </>
            )}
          </>
        }
        actions={
          openCreate &&
          count !== null &&
          count > 0 && (
            <Button id={newProjectId} onClick={openCreate}>
              <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
              New project
            </Button>
          )
        }
      />
      {content}
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
