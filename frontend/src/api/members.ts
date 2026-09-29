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

/**
 * POST /api/workspaces/{id}/members. There is no role and no workspace:
 * the backend always creates a MEMBER of the caller's own workspace.
 */
export interface CreateMemberRequest {
  /** Trimmed by the backend, at most 255 characters. */
  name: string
  /** Trimmed and lower-cased by the backend; unique across QueueFlow in any letter case. */
  email: string
  /** Used exactly as given: at least 8 characters, at most 72 bytes in UTF-8. */
  password: string
}

/** Everyone in the caller's workspace (the caller included), ordered case-insensitively by name. Both roles may read it. */
export function listMembers(request: AuthorizedRequest, workspaceId: string, signal?: AbortSignal): Promise<Member[]> {
  return request<Member[]>(`/api/workspaces/${encodeURIComponent(workspaceId)}/members`, { signal })
}

/**
 * ADMIN only (403 otherwise). An email already registered anywhere is 409.
 * The new member signs in straight away with this password; no email is
 * sent and the response never contains the password.
 */
export function createMember(
  request: AuthorizedRequest,
  workspaceId: string,
  body: CreateMemberRequest,
): Promise<Member> {
  return request<Member>(`/api/workspaces/${encodeURIComponent(workspaceId)}/members`, { method: 'POST', body })
}

/** PATCH /api/workspaces/{id}/members/{userId}: the name is the only thing that can change. */
export interface UpdateMemberRequest {
  /** Trimmed by the backend, at most 255 characters. */
  name: string
}

function memberPath(workspaceId: string, userId: string): string {
  return `/api/workspaces/${encodeURIComponent(workspaceId)}/members/${encodeURIComponent(userId)}`
}

/** ADMIN only (403 otherwise); a user who is not (or no longer) in the workspace is 404. */
export function updateMember(
  request: AuthorizedRequest,
  workspaceId: string,
  userId: string,
  body: UpdateMemberRequest,
): Promise<Member> {
  return request<Member>(memberPath(workspaceId, userId), { method: 'PATCH', body })
}

/**
 * ADMIN only (403 otherwise), for a MEMBER other than the caller (400
 * otherwise). The member loses access at once - their password and any
 * token they hold stop working - and their assigned tickets become
 * unassigned; what they created, wrote and did stays.
 */
export function removeMember(request: AuthorizedRequest, workspaceId: string, userId: string): Promise<void> {
  return request<void>(memberPath(workspaceId, userId), { method: 'DELETE' })
}
