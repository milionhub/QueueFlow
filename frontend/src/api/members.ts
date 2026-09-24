import type { UserRole } from '../features/auth/types'
import type { AuthorizedRequest } from './client'

/** The backend's UserResponse, as listed for a workspace. */
export interface Member {
  id: string
  name: string
  email: string
  role: UserRole
  workspaceId: string
  createdAt: string
  updatedAt: string
}

/** Everyone in the caller's workspace (the caller included), ordered case-insensitively by name. Both roles may read it. */
export function listMembers(request: AuthorizedRequest, workspaceId: string, signal?: AbortSignal): Promise<Member[]> {
  return request<Member[]>(`/api/workspaces/${encodeURIComponent(workspaceId)}/members`, { signal })
}
