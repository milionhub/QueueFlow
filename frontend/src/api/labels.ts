import type { AuthorizedRequest } from './client'

/** The backend's LabelResponse. Labels belong to the workspace, not to a project. */
export interface Label {
  id: string
  /** Trimmed; unique within the workspace, case-sensitively ("Bug" and "bug" may coexist). */
  name: string
  workspaceId: string
  createdAt: string
  updatedAt: string
}

/** The workspace's labels, ordered case-insensitively by name - keep that order. */
export function listLabels(request: AuthorizedRequest, workspaceId: string, signal?: AbortSignal): Promise<Label[]> {
  return request<Label[]>(`/api/workspaces/${encodeURIComponent(workspaceId)}/labels`, { signal })
}

/** Any member may create one. The backend trims the name; a name already used in the workspace is 409. */
export function createLabel(request: AuthorizedRequest, name: string): Promise<Label> {
  return request<Label>('/api/labels', { method: 'POST', body: { name } })
}
