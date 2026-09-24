import type { MouseEvent } from 'react'

import { Button } from '../../../components/ui/Button'

/** No projects yet. Only an ADMIN can create one, so only an ADMIN gets the action. */
export function ProjectsEmptyState({ onCreate }: { onCreate?: (event: MouseEvent<HTMLButtonElement>) => void }) {
  return (
    <section aria-labelledby="projects-empty" className="max-w-lg rounded-md border border-line bg-surface px-5 py-4">
      <h2 id="projects-empty" className="text-sm font-semibold text-ink">
        No projects yet
      </h2>
      <p className="mt-1.5 text-sm leading-6 text-ink-muted">
        Projects group your team's tickets. Each one has a short key, like{' '}
        <span className="font-mono text-xs text-ink">CORE</span>, that prefixes its ticket IDs (
        <span className="font-mono text-xs text-ink">CORE-7</span>).
      </p>
      <div className="mt-3 border-t border-line pt-3">
        {onCreate ? (
          <Button onClick={onCreate}>New project</Button>
        ) : (
          <p className="text-sm text-ink-muted">A workspace admin can create the first project.</p>
        )}
      </div>
    </section>
  )
}
