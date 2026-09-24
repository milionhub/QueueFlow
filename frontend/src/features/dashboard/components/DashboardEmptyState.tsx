import type { UserRole } from '../../auth/types'

/**
 * A workspace without projects (and so without tickets): one honest panel
 * instead of empty sections. Only an ADMIN can create projects, so the next
 * step it points to depends on the role. No button until project creation
 * exists.
 */
export function DashboardEmptyState({ role }: { role: UserRole }) {
  return (
    <section aria-labelledby="dashboard-empty" className="max-w-lg rounded-md border border-line bg-surface px-5 py-4">
      <h2 id="dashboard-empty" className="text-sm font-semibold text-ink">
        No projects yet
      </h2>
      <p className="mt-1.5 text-sm leading-6 text-ink-muted">
        Projects group your team's tickets. Each one has a short key, like{' '}
        <span className="font-mono text-xs text-ink">CORE</span>, that prefixes its ticket IDs (
        <span className="font-mono text-xs text-ink">CORE-7</span>).
      </p>
      <p className="mt-3 border-t border-line pt-3 text-sm text-ink-muted">
        {role === 'ADMIN'
          ? 'Project creation is coming to the Projects section.'
          : 'A workspace admin can create the first project.'}
      </p>
    </section>
  )
}
