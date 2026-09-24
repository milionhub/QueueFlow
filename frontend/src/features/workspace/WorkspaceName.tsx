import { useCurrentWorkspace } from './useCurrentWorkspace'

/** The workspace's name for a context line: a placeholder while it loads, never its id. */
export function WorkspaceName() {
  const { status, workspace } = useCurrentWorkspace()
  if (status === 'loading') {
    return (
      <span aria-hidden="true" className="inline-block h-3 w-24 rounded bg-line align-middle motion-safe:animate-pulse" />
    )
  }
  return <span className="font-medium text-ink">{workspace?.name ?? 'Your workspace'}</span>
}
