import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <section aria-labelledby="not-found-title" className="max-w-2xl">
      <p className="text-sm font-medium text-ink-subtle">404</p>
      <h1 id="not-found-title" className="mt-2 text-2xl font-semibold tracking-tight text-ink">
        Page not found
      </h1>
      <p className="mt-2 text-sm text-ink-muted">The page you are looking for does not exist.</p>
      <Link to="/" className="mt-6 inline-block text-sm font-medium text-accent underline-offset-4 hover:underline">
        Back to start
      </Link>
    </section>
  )
}
