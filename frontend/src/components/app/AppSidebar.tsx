import { FolderKanban, LayoutDashboard, Users, X, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router'

import { useCurrentWorkspace } from '../../features/workspace/useCurrentWorkspace'
import { avatarTint } from '../../features/auth/userDisplay'
import { QueueFlowLogo } from '../QueueFlowMark'
import { IconButton } from '../ui/IconButton'
import { UserMenu } from './UserMenu'

interface NavEntry {
  /** The entry's name: a message in shell.nav. */
  labelKey: 'dashboard' | 'projects' | 'members'
  icon: LucideIcon
  to: string
  /** Active only on exactly `to`, not on the pages below it. */
  end?: boolean
}

/**
 * The product's sections. Boards belong to a project: they are reached from
 * the project's own List | Board navigation, not from here.
 */
const NAVIGATION: NavEntry[] = [
  { labelKey: 'dashboard', icon: LayoutDashboard, to: '/app', end: true },
  { labelKey: 'projects', icon: FolderKanban, to: '/app/projects' },
  { labelKey: 'members', icon: Users, to: '/app/members' },
]

interface AppSidebarProps {
  /** Called when a link is followed (closes the mobile drawer). */
  onNavigate?: () => void
  /** Mobile drawer only: renders a close button. */
  onClose?: () => void
  /** In the mobile drawer: taller rows for touch, and room for the device's safe areas. */
  drawer?: boolean
}

export function AppSidebar({ onNavigate, onClose, drawer = false }: AppSidebarProps) {
  const { t } = useTranslation('shell')
  const row = drawer ? 'h-11 text-[15px]' : 'h-9 text-sm'
  return (
    <div
      className={`flex h-full flex-col ${drawer ? 'pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)]' : ''}`}
    >
      <div className="flex h-14 shrink-0 items-center justify-between gap-2 pr-3 pl-5">
        <QueueFlowLogo size="sm" />
        {onClose && <IconButton icon={X} label={t('closeNavigation')} title="" onClick={onClose} />}
      </div>

      <WorkspaceIdentity />

      <nav aria-label={t('nav.main')} className="mt-3 flex-1 overflow-y-auto px-3">
        <ul className="flex flex-col gap-0.5">
          {NAVIGATION.map(({ labelKey, icon: Icon, to, end }) => (
            <li key={labelKey}>
              <NavLink
                to={to}
                end={end}
                onClick={onNavigate}
                className={({ isActive }) =>
                  `press relative flex items-center gap-2.5 rounded-md px-2.5 transition-colors duration-150 ${row} ${
                    isActive
                      ? 'bg-accent-subtle font-semibold text-accent before:absolute before:inset-y-2 before:-left-3 before:w-[3px] before:rounded-r-full before:bg-accent'
                      : 'font-medium text-ink-muted hover:bg-canvas-strong hover:text-ink'
                  }`
                }
              >
                <Icon aria-hidden="true" className="size-4 shrink-0" strokeWidth={2} />
                {t(`nav.${labelKey}`)}
              </NavLink>
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
 * The workspace's real name, from the backend, with its initial on a
 * square tile. The tile's tint follows the workspace's id (the same quiet
 * palette as people's avatars) - one system with the rest of QueueFlow, but
 * never mistaken for the solid indigo QueueFlow mark. While it loads, or if
 * it cannot be fetched, a neutral "Your workspace" - never a raw id or a guess.
 */
function WorkspaceIdentity() {
  const { t } = useTranslation()
  const { status, workspace } = useCurrentWorkspace()
  const name = workspace?.name ?? t('workspace.fallback')
  const initial = workspace ? ([...workspace.name.trim()].find((char) => /[\p{L}\p{N}]/u.test(char)) ?? '·') : null
  const tint = workspace ? avatarTint(workspace.id) : 'bg-canvas-strong text-ink-muted'

  return (
    <div className="mx-3 flex items-center gap-2.5 rounded-lg border border-line bg-surface px-2.5 py-2 shadow-xs">
      <span
        aria-hidden="true"
        className={`flex size-8 shrink-0 items-center justify-center rounded-md text-sm font-bold ring-1 ring-black/5 ring-inset ${tint}`}
      >
        {status === 'loading' ? '' : (initial?.toLocaleUpperCase() ?? '·')}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[11px] leading-4 font-medium tracking-wide text-ink-muted uppercase">{t('workspace.label')}</p>
        {status === 'loading' ? (
          <div aria-hidden="true" className="skeleton mt-1 h-3.5 w-28" />
        ) : (
          <p className="truncate text-sm leading-5 font-semibold text-ink" title={name}>
            {name}
          </p>
        )}
      </div>
    </div>
  )
}
