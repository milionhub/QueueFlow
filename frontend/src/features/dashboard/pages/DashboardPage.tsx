import type { DashboardResponse } from '../../../api/dashboard'
import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import type { CurrentUser } from '../../auth/types'
import { useAuth } from '../../auth/useAuth'
import { useCurrentWorkspace } from '../../workspace/useCurrentWorkspace'
import { DashboardEmptyState } from '../components/DashboardEmptyState'
import { DashboardSkeleton } from '../components/DashboardSkeleton'
import { ProjectSummaryList } from '../components/ProjectSummaryList'
import { SectionHeading } from '../components/SectionHeading'
import { StatusSummary } from '../components/StatusSummary'
import { TicketList } from '../components/TicketList'
import { useDashboard, type DashboardState } from '../useDashboard'

/**
 * /app: the signed-in workspace at a glance, from one request
 * (GET /api/workspaces/{id}/dashboard). The shell's header already says
 * "Dashboard", so the page has no heading of its own.
 */
export function DashboardPage() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  return <Dashboard user={user} />
}

function Dashboard({ user }: { user: CurrentUser }) {
  const { state, retry } = useDashboard(user.workspaceId)

  return (
    <div className="max-w-6xl">
      <DashboardContent state={state} retry={retry} user={user} />
    </div>
  )
}

function DashboardContent({ state, retry, user }: { state: DashboardState; retry: () => void; user: CurrentUser }) {
  switch (state.status) {
    case 'loading':
      return <DashboardSkeleton />
    case 'error':
      return (
        <div className="flex max-w-lg flex-col items-start gap-3">
          <Alert tone="error">
            The dashboard could not be loaded.{' '}
            {state.reason === 'network'
              ? 'The server could not be reached.'
              : 'The server ran into a problem.'}{' '}
            Please try again.
          </Alert>
          <Button variant="secondary" onClick={retry}>
            Retry
          </Button>
        </div>
      )
    case 'ready':
      return state.dashboard.projects.length === 0 ? (
        <DashboardEmptyState role={user.role} />
      ) : (
        <PopulatedDashboard dashboard={state.dashboard} now={state.receivedAt} />
      )
  }
}

function PopulatedDashboard({ dashboard, now }: { dashboard: DashboardResponse; now: number }) {
  // Derived from counts the backend already aggregated - no extra request.
  const totalTickets = dashboard.statusCounts.reduce((sum, { count }) => sum + count, 0)
  const doneTickets = dashboard.statusCounts.find(({ status }) => status === 'DONE')?.count ?? 0
  const openTickets = totalTickets - doneTickets

  const projects = (
    <section aria-labelledby="dashboard-projects">
      <SectionHeading id="dashboard-projects" title="Projects" />
      <ProjectSummaryList projects={dashboard.projects} />
    </section>
  )

  if (totalTickets === 0) {
    return (
      <div className="flex max-w-2xl flex-col gap-6">
        <div>
          <ContextLine projectCount={dashboard.projects.length} openTickets={openTickets} />
          <p className="mt-1 text-sm text-ink-muted">No tickets yet.</p>
        </div>
        {projects}
      </div>
    )
  }

  const { assignedToMe } = dashboard
  return (
    <div className="flex flex-col gap-6">
      <ContextLine projectCount={dashboard.projects.length} openTickets={openTickets} />
      <StatusSummary statusCounts={dashboard.statusCounts} unassignedOpenCount={dashboard.unassignedOpenCount} />

      <div className="grid grid-cols-1 items-start gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-6">
          <section aria-labelledby="dashboard-assigned">
            <SectionHeading id="dashboard-assigned" title="Assigned to you" detail={`${assignedToMe.openCount} open`}>
              {assignedToMe.openCount > assignedToMe.tickets.length &&
                `Showing ${assignedToMe.tickets.length} of ${assignedToMe.openCount}`}
            </SectionHeading>
            {assignedToMe.tickets.length > 0 ? (
              <TicketList tickets={assignedToMe.tickets} detail="priority" now={now} />
            ) : (
              <p className="rounded-md border border-line bg-surface px-4 py-3 text-sm text-ink-muted">
                {openTickets === 0 ? (
                  'No open tickets assigned to you.'
                ) : (
                  <>
                    Nothing assigned to you.
                    {dashboard.unassignedOpenCount > 0 &&
                      ` ${dashboard.unassignedOpenCount} open ${
                        dashboard.unassignedOpenCount === 1 ? 'ticket is' : 'tickets are'
                      } unassigned.`}
                  </>
                )}
              </p>
            )}
          </section>

          <section aria-labelledby="dashboard-recent">
            <SectionHeading id="dashboard-recent" title="Recently updated" />
            <TicketList tickets={dashboard.recentlyUpdated} detail="assignee" now={now} />
          </section>
        </div>

        {projects}
      </div>
    </div>
  )
}

/** "Acme · 3 projects · 18 open tickets" - the workspace name from the shell's context, never its id. */
function ContextLine({ projectCount, openTickets }: { projectCount: number; openTickets: number }) {
  const { status, workspace } = useCurrentWorkspace()
  return (
    <p className="text-sm text-ink-muted">
      {status === 'loading' ? (
        <span aria-hidden="true" className="inline-block h-3 w-24 rounded bg-line align-middle motion-safe:animate-pulse" />
      ) : (
        <span className="font-medium text-ink">{workspace?.name ?? 'Your workspace'}</span>
      )}
      {' · '}
      {projectCount} {projectCount === 1 ? 'project' : 'projects'}
      {' · '}
      {openTickets} open {openTickets === 1 ? 'ticket' : 'tickets'}
    </p>
  )
}
