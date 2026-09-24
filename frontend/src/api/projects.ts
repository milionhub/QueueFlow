import type { AuthorizedRequest } from './client'

/** The backend's ProjectResponse. */
export interface Project {
  id: string
  name: string
  /** 2–10 of A-Z and 0-9; fixed at creation, it prefixes every ticket ID (CORE-7). */
  key: string
  description: string | null
  workspaceId: string
  createdAt: string
  updatedAt: string
}

/** POST /api/projects: always created in the caller's workspace; the backend trims the name and upper-cases the key. */
export interface CreateProjectRequest {
  name: string
  key: string
  description: string | null
}

/**
 * PATCH /api/projects/{id}: an omitted field stays as it is; `description:
 * null` clears it. There is no key: it cannot be changed.
 */
export interface UpdateProjectRequest {
  name?: string
  description?: string | null
}

/** Every project of the caller's workspace, ordered case-insensitively by name - keep that order. */
export function listProjects(
  request: AuthorizedRequest,
  workspaceId: string,
  signal?: AbortSignal,
): Promise<Project[]> {
  return request<Project[]>(`/api/workspaces/${encodeURIComponent(workspaceId)}/projects`, { signal })
}

/**
 * A project of the caller's workspace by its key. The backend trims and
 * upper-cases the key; an unknown key - or another workspace's - is 404.
 */
export function getProjectByKey(request: AuthorizedRequest, key: string, signal?: AbortSignal): Promise<Project> {
  return request<Project>(`/api/projects/by-key?key=${encodeURIComponent(key)}`, { signal })
}

/** ADMIN only: a MEMBER gets 403, a key already used in the workspace 409. */
export function createProject(request: AuthorizedRequest, body: CreateProjectRequest): Promise<Project> {
  return request<Project>('/api/projects', { method: 'POST', body })
}

/** ADMIN only: a MEMBER gets 403; a project outside the caller's workspace 404. */
export function updateProject(
  request: AuthorizedRequest,
  projectId: string,
  body: UpdateProjectRequest,
): Promise<Project> {
  return request<Project>(`/api/projects/${encodeURIComponent(projectId)}`, { method: 'PATCH', body })
}
