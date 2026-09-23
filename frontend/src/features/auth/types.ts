/** The two fixed backend roles (UserRole). */
export type UserRole = 'ADMIN' | 'MEMBER'

/**
 * The backend's UserResponse: returned by GET /api/auth/me and inside every
 * AuthResponse. The current user is always this object from the backend,
 * never something decoded from the access token.
 */
export interface CurrentUser {
  id: string
  name: string
  email: string
  role: UserRole
  workspaceId: string
  createdAt: string
  updatedAt: string
}

/** POST /api/auth/login */
export interface LoginRequest {
  email: string
  password: string
}

/** POST /api/auth/register: creates a new workspace and its first user (ADMIN). */
export interface RegisterRequest {
  name: string
  email: string
  password: string
  workspaceName: string
}

/** Returned by both login and register. */
export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  /** Seconds until the access token expires. */
  expiresIn: number
  user: CurrentUser
}
