import type { AuthorizedRequest } from './client'

/** The backend's WorkspaceResponse. */
export interface Workspace {
  id: string
  name: string
  createdAt: string
  updatedAt: string
}

/** GET /api/workspaces/{id}: only the caller's own workspace; any other id is 404. */
export function getWorkspace(request: AuthorizedRequest, workspaceId: string, signal?: AbortSignal): Promise<Workspace> {
  return request<Workspace>(`/api/workspaces/${encodeURIComponent(workspaceId)}`, { signal })
}
