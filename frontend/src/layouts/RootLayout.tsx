import { Link, Outlet } from 'react-router'

/**
 * The frame every page renders in. Deliberately minimal for now: the
 * application shell (navigation, workspace, user) arrives with the
 * authenticated pages, as a layout route nested under this one.
 */
export function RootLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex h-14 w-full max-w-5xl items-center px-4 sm:px-6">
          <Link to="/" className="rounded-sm text-base font-semibold tracking-tight text-ink">
            QueueFlow
          </Link>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-10 sm:px-6 sm:py-16">
        <Outlet />
      </main>
    </div>
  )
}
