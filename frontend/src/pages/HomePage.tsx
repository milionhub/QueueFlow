import { useAuth } from '../features/auth/useAuth'
import { useCurrentWorkspace } from '../features/workspace/useCurrentWorkspace'

/**
 * Temporary (Phase 3.3) content of /app, inside the permanent shell: a
 * welcome with real data only. Replaced by the dashboard in Phase 3.4.
 */
export function HomePage() {
  const { user } = useAuth()
  const { workspace } = useCurrentWorkspace()

  if (!user) {
    return null
  }

  return (
    <section aria-labelledby="home-welcome" className="max-w-2xl">
      <h2 id="home-welcome" className="text-xl font-semibold tracking-tight text-ink">
        Welcome, {user.name}
      </h2>
      <p className="mt-2 text-sm leading-6 text-ink-muted">
        {workspace ? (
          <>
            <span className="font-medium text-ink">{workspace.name}</span> is ready.
          </>
        ) : (
          'Your workspace is ready.'
        )}{' '}
        Projects, tickets and the board will appear here as they are built.
      </p>

      <div className="mt-8 rounded-lg border border-dashed border-line px-5 py-6">
        <p className="text-sm font-medium text-ink">Dashboard coming next</p>
        <p className="mt-1 text-sm leading-6 text-ink-muted">
          This space will show your workspace's projects and the tickets that need attention.
        </p>
      </div>
    </section>
  )
}
