import { useNavigate } from 'react-router'

import { Button } from '../components/ui/Button'
import { useAuth } from '../features/auth/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

const ROLE_LABELS = { ADMIN: 'Admin', MEMBER: 'Member' } as const

/**
 * Temporary (Phase 3.2): proves the session end to end with the current
 * user exactly as GET /api/auth/me / the AuthResponse describe it. Replaced
 * by the application shell in Phase 3.3.
 */
export function AppHomePage() {
  useDocumentTitle('Home')
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  if (!user) {
    return null
  }

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <section aria-labelledby="app-title" className="max-w-2xl">
      <p className="text-sm text-ink-muted">Signed in as</p>
      <div className="mt-1 flex flex-wrap items-center gap-3">
        <h1 id="app-title" className="text-2xl font-semibold tracking-tight break-words text-ink">
          {user.name}
        </h1>
        <span className="rounded-full border border-accent/25 bg-accent/5 px-2.5 py-0.5 text-xs font-medium text-accent">
          {ROLE_LABELS[user.role]}
        </span>
      </div>

      <dl className="mt-8 divide-y divide-line rounded-lg border border-line bg-surface text-sm">
        <div className="flex flex-col gap-1 px-4 py-3 sm:flex-row sm:justify-between sm:gap-4">
          <dt className="text-ink-muted">Email</dt>
          <dd className="min-w-0 break-all text-ink">{user.email}</dd>
        </div>
        <div className="flex flex-col gap-1 px-4 py-3 sm:flex-row sm:justify-between sm:gap-4">
          <dt className="text-ink-muted">Workspace ID</dt>
          <dd className="min-w-0 font-mono text-xs break-all text-ink">{user.workspaceId}</dd>
        </div>
      </dl>

      <p className="mt-6 text-sm text-ink-muted">The application itself comes next.</p>

      <Button variant="secondary" onClick={handleLogout} className="mt-6">
        Sign out
      </Button>
    </section>
  )
}
