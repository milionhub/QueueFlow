/**
 * Shown when rendering a route throws. Replaces React Router's developer
 * error screen with a plain message; no error details reach the page.
 * Rendered outside RootLayout (the layout itself may be what failed), so it
 * links with a plain anchor to reload the app from a clean state.
 */
export function RouteErrorPage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-5xl flex-col justify-center px-4 sm:px-6">
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Something went wrong</h1>
      <p className="mt-2 text-sm text-ink-muted">An unexpected error occurred while showing this page.</p>
      <a href="/" className="mt-6 text-sm font-medium text-accent underline-offset-4 hover:underline">
        Back to start
      </a>
    </main>
  )
}
