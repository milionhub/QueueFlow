import { QueueFlowLogo } from '../../../components/QueueFlowMark'
import { Button } from '../../../components/ui/Button'
import { useDocumentTitle } from '../../../hooks/useDocumentTitle'
import { useAuth } from '../useAuth'

/** While the stored session is being checked: nothing protected or guest-only is shown yet. */
export function SessionLoadingScreen() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-5 px-4" role="status">
      <QueueFlowLogo />
      <div aria-hidden="true" className="h-0.5 w-24 overflow-hidden rounded-full bg-line">
        <div className="h-full w-1/3 animate-[queueflow-progress_1.2s_ease-in-out_infinite] rounded-full bg-accent" />
      </div>
      <span className="sr-only">Loading QueueFlow…</span>
    </div>
  )
}

/**
 * A session exists but the backend could not confirm it (server down,
 * network, 5xx). The token is kept: retrying can pick the session up again.
 */
export function SessionUnavailableScreen() {
  useDocumentTitle('Connection problem')
  const { retry, logout } = useAuth()

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-4 py-12">
      <main className="w-full max-w-sm text-center">
        <div className="flex justify-center">
          <QueueFlowLogo />
        </div>
        <h1 className="mt-8 text-xl font-semibold tracking-tight text-ink">We couldn't reach QueueFlow</h1>
        <p className="mt-2 text-sm leading-6 text-ink-muted" role="alert">
          The server isn't responding right now, so your session couldn't be checked. You're still signed in; try again in
          a moment.
        </p>
        <div className="mt-6 flex flex-col items-center gap-3">
          <Button size="lg" onClick={retry} className="w-full">
            Try again
          </Button>
          <button
            type="button"
            onClick={logout}
            className="rounded-sm text-sm font-medium text-ink-muted underline-offset-4 hover:text-ink hover:underline"
          >
            Sign out
          </button>
        </div>
      </main>
    </div>
  )
}
