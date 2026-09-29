import { useCurrentWorkspace } from './useCurrentWorkspace'

/** The workspace's name for a context line: a placeholder while it loads, never its id. */
export function WorkspaceName() {
  const { status, workspace } = useCurrentWorkspace()
  if (status === 'loading') {
    return <span aria-hidden="true" className="skeleton inline-block h-3 w-24 align-middle" />
  }
  return <span className="font-medium text-ink">{workspace?.name ?? 'Your workspace'}</span>
}
