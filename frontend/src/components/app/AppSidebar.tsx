import { FolderKanban, LayoutDashboard, Users, X, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router'

import { useCurrentWorkspace } from '../../features/workspace/useCurrentWorkspace'
import { QueueFlowMark } from '../QueueFlowMark'
import { UserMenu } from './UserMenu'

interface NavEntry {
  label: string
  icon: LucideIcon
  /** Absent for sections that are not built yet: shown, but not a link. */
  to?: string
  /** Active only on exactly `to`, not on the pages below it. */
  end?: boolean
}

/**
 * The product's sections. Only those with a route are links; the rest are
 * marked "Soon". Boards belong to a project: they are reached from the
 * project's own List | Board navigation, not from here.
 */
const NAVIGATION: NavEntry[] = [
  { label: 'Dashboard', icon: LayoutDashboard, to: '/app', end: true },
  { label: 'Projects', icon: FolderKanban, to: '/app/projects' },
  { label: 'Members', icon: Users, to: '/app/members' },
]

const ROW = 'flex h-8 items-center gap-2.5 rounded-md px-2 text-sm'

interface AppSidebarProps {
  /** Called when a link is followed (closes the mobile drawer). */
  onNavigate?: () => void
  /** Mobile drawer only: renders a close button. */
  onClose?: () => void
}

export function AppSidebar({ onNavigate, onClose }: AppSidebarProps) {
  return (
    <div className="flex h-full flex-col bg-canvas">
      <div className="flex h-14 shrink-0 items-center justify-between gap-2 pr-3 pl-5">
        <span className="inline-flex items-center gap-2">
          <QueueFlowMark className="size-6" />
          <span className="text-[15px] font-semibold tracking-tight text-ink">QueueFlow</span>
        </span>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Close navigation"
            className="inline-flex size-8 items-center justify-center rounded-md text-ink-muted hover:bg-line/60 hover:text-ink"
          >
            <X aria-hidden="true" className="size-4" strokeWidth={2} />
          </button>
        )}
      </div>

      <WorkspaceIdentity />

      <nav aria-label="Main" className="mt-4 flex-1 overflow-y-auto px-3">
        <ul className="flex flex-col gap-0.5">
          {NAVIGATION.map(({ label, icon: Icon, to, end }) => (
            <li key={label}>
              {to ? (
                <NavLink
                  to={to}
                  end={end}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    `${ROW} ${
                      isActive
                        ? 'bg-accent-subtle font-semibold text-accent'
                        : 'font-medium text-ink-muted hover:bg-line/60 hover:text-ink'
                    }`
                  }
                >
                  <Icon aria-hidden="true" className="size-4 shrink-0" strokeWidth={2} />
                  {label}
                </NavLink>
              ) : (
                <span className={`${ROW} cursor-default font-medium text-ink-subtle`}>
                  <Icon aria-hidden="true" className="size-4 shrink-0" strokeWidth={2} />
                  {label}
                  <span className="ml-auto text-[11px] font-medium text-ink-subtle">
                    Soon<span className="sr-only"> (not available yet)</span>
                  </span>
                </span>
              )}
            </li>
          ))}
        </ul>
      </nav>

      <div className="shrink-0 border-t border-line p-3">
        <UserMenu />
      </div>
    </div>
  )
}

/**
 * The workspace's real name, from the backend. While it loads, or if it
 * cannot be fetched, a neutral "Your workspace" - never a raw id or a guess.
 */
function WorkspaceIdentity() {
  const { status, workspace } = useCurrentWorkspace()

  return (
    <div className="px-5">
      <p className="text-[11px] font-medium tracking-wide text-ink-subtle uppercase">Workspace</p>
      {status === 'loading' ? (
        <div aria-hidden="true" className="mt-1.5 h-4 w-32 animate-pulse rounded bg-line" />
      ) : (
        <p className="mt-0.5 truncate text-sm font-semibold text-ink" title={workspace?.name}>
          {workspace?.name ?? 'Your workspace'}
        </p>
      )}
    </div>
  )
}
