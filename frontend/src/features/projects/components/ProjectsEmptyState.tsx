import { FolderPlus, Plus } from 'lucide-react'
import type { MouseEvent } from 'react'

import { Button } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/States'

/** No projects yet. Only an ADMIN can create one, so only an ADMIN gets the action. */
export function ProjectsEmptyState({ onCreate }: { onCreate?: (event: MouseEvent<HTMLButtonElement>) => void }) {
  return (
    <EmptyState
      icon={FolderPlus}
      title="No projects yet"
      action={
        onCreate && (
          <Button onClick={onCreate}>
            <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
            New project
          </Button>
        )
      }
    >
      Projects group your team's tickets. Each one has a short key, like{' '}
      <span className="font-mono text-xs text-ink">CORE</span>, that prefixes its ticket IDs (
      <span className="font-mono text-xs text-ink">CORE-7</span>).
      {!onCreate && ' A workspace admin can create the first project.'}
    </EmptyState>
  )
}
