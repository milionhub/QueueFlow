import { CircleCheck, FolderKanban, History, Inbox } from 'lucide-react'
import { Link } from 'react-router'

import type { DashboardResponse } from '../../../api/dashboard'
import { LoadError } from '../../../components/ui/LoadError'
import { PageHeader } from '../../../components/ui/PageHeader'
import { EmptyState } from '../../../components/ui/States'
import type { CurrentUser } from '../../auth/types'
import { useAuth } from '../../auth/useAuth'
import { WorkspaceName } from '../../workspace/WorkspaceName'
import { DashboardEmptyState } from '../components/DashboardEmptyState'
import { DashboardSkeleton } from '../components/DashboardSkeleton'
import { ProjectSummaryList } from '../components/ProjectSummaryList'
import { SectionCard } from '../components/SectionHeading'
import { StatusSummary } from '../components/StatusSummary'
import { TicketList } from '../components/TicketList'
import { useDashboard, type DashboardState } from '../useDashboard'

/**
 * /app: the signed-in workspace at a glance, from one request
 * (GET /api/workspaces/{id}/dashboard). Nothing here is invented: every
 * number and bar is the backend's own counts.
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
  const counts = state.status === 'ready' ? summarize(state.dashboard) : null

  return (
    <div className="flex max-w-7xl flex-col gap-6">
      <PageHeader
        title="Dashboard"
        description={
          <>
            <WorkspaceName />
            {counts && counts.projects > 0 && (
              <>
                {' · '}
                {counts.projects} {counts.projects === 1 ? 'project' : 'projects'}
                {' · '}
                {counts.open} open {counts.open === 1 ? 'ticket' : 'tickets'}
              </>
            )}
          </>
        }
      />
      <DashboardContent state={state} retry={retry} user={user} />
    </div>
  )
}

function summarize(dashboard: DashboardResponse) {
  // Derived from counts the backend already aggregated - no extra request.
  const total = dashboard.statusCounts.reduce((sum, { count }) => sum + count, 0)
  const done = dashboard.statusCounts.find(({ status }) => status === 'DONE')?.count ?? 0
  return { projects: dashboard.projects.length, total, open: total - done }
}

function DashboardContent({ state, retry, user }: { state: DashboardState; retry: () => void; user: CurrentUser }) {
  switch (state.status) {
    case 'loading':
      return <DashboardSkeleton />
    case 'error':
      return <LoadError message="The dashboard could not be loaded." reason={state.reason} onRetry={retry} />
    case 'ready':
      return state.dashboard.projects.length === 0 ? (
        <DashboardEmptyState role={user.role} />
      ) : (
        <PopulatedDashboard dashboard={state.dashboard} now={state.receivedAt} />
      )
  }
}

/**
 * Two columns from `xl`: your tickets and recent changes on the left,
 * status and projects on the right. On narrower screens one column, in
 * order of use: your tickets, status, recent changes, projects. (The
 * column wrappers dissolve with `display: contents` so `order` can
 * interleave them.)
 */
function PopulatedDashboard({ dashboard, now }: { dashboard: DashboardResponse; now: number }) {
  const { total, open } = summarize(dashboard)

  const projects = (
    <SectionCard
      id="dashboard-projects"
      title="Projects"
      icon={FolderKanban}
      tone="success"
      className="order-4"
      aside={
        <Link to="/app/projects" className="rounded-sm font-medium text-accent underline-offset-4 hover:underline">
          View all<span className="sr-only"> projects</span>
        </Link>
      }
    >
      <ProjectSummaryList projects={dashboard.projects} />
    </SectionCard>
  )

  if (total === 0) {
    return (
      <div className="flex max-w-2xl flex-col gap-5">
        <EmptyState icon={CircleCheck} title="No tickets yet">
          Tickets created in any project will show up here.
        </EmptyState>
        {projects}
      </div>
    )
  }

  const { assignedToMe } = dashboard
  return (
    <div className="flex flex-col gap-5 xl:grid xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] xl:items-start">
      <div className="contents xl:flex xl:min-w-0 xl:flex-col xl:gap-5">
        <SectionCard
          id="dashboard-assigned"
          title="Assigned to you"
          icon={Inbox}
          tone="accent"
          primary
          count={`${assignedToMe.openCount} open`}
          className="order-1"
          footer={
            assignedToMe.openCount > assignedToMe.tickets.length &&
            `Showing ${assignedToMe.tickets.length} of ${assignedToMe.openCount}`
          }
        >
          {assignedToMe.tickets.length > 0 ? (
            <TicketList tickets={assignedToMe.tickets} detail="priority" now={now} />
          ) : (
            <EmptyState icon={CircleCheck} title="You're all caught up" tone="plain" as="h3" className="py-8">
              {open === 0 ? (
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
            </EmptyState>
          )}
        </SectionCard>

        <SectionCard id="dashboard-recent" title="Recently updated" icon={History} tone="info" className="order-3">
          <TicketList tickets={dashboard.recentlyUpdated} detail="assignee" now={now} />
        </SectionCard>
      </div>

      <div className="contents xl:flex xl:min-w-0 xl:flex-col xl:gap-5">
        <StatusSummary
          statusCounts={dashboard.statusCounts}
          unassignedOpenCount={dashboard.unassignedOpenCount}
          className="order-2"
        />
        {projects}
      </div>
    </div>
  )
}
