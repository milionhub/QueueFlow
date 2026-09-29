import { FolderPlus } from 'lucide-react'
import { Link } from 'react-router'

import { buttonLinkClasses } from '../../../components/ui/buttonStyles'
import { EmptyState } from '../../../components/ui/States'
import type { UserRole } from '../../auth/types'

/**
 * A workspace without projects (and so without tickets): one honest panel
 * instead of empty sections. Only an ADMIN can create projects, so the next
 * step it points to depends on the role.
 */
export function DashboardEmptyState({ role }: { role: UserRole }) {
  return (
    <EmptyState
      icon={FolderPlus}
      title="No projects yet"
      className="max-w-2xl"
      action={
        role === 'ADMIN' ? (
          <Link to="/app/projects" className={buttonLinkClasses('primary')}>
            Go to Projects
          </Link>
        ) : undefined
      }
    >
      Projects group your team's tickets. Each one has a short key, like{' '}
      <span className="font-mono text-xs text-ink">CORE</span>, that prefixes its ticket IDs (
      <span className="font-mono text-xs text-ink">CORE-7</span>).{' '}
      {role === 'ADMIN' ? 'Create your first project in Projects.' : 'A workspace admin can create the first project.'}
    </EmptyState>
  )
}
