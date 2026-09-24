import { createContext } from 'react'

import type { AuthorizedRequest } from '../../api/client'
import type { CurrentUser, LoginRequest, RegisterRequest } from './types'

/**
 * - `checking`: a stored token is being verified with GET /api/auth/me
 * - `authenticated`: `user` is the backend's current user
 * - `unauthenticated`: no token, or the backend rejected it
 * - `unavailable`: a token exists but the backend could not be asked about
 *   it (down, unreachable, 5xx). The token is kept: this is not a verdict on
 *   the credentials, and `retry` checks again.
 */
export type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated' | 'unavailable'

/** Why the user was signed out without asking for it. */
export type SignOutReason = 'session-expired'

export interface AuthContextValue {
  status: AuthStatus
  user: CurrentUser | null
  signOutReason: SignOutReason | null
  login: (request: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  /** Client-side only: forgets the token. The backend has no logout endpoint; the token itself stays valid until it expires. */
  logout: () => void
  /**
   * Calls the backend as the signed-in user. A 401 means the token is no
   * longer accepted: the session ends (the guards then show /login with a
   * notice) and the error is rethrown to the caller.
   */
  authorizedRequest: AuthorizedRequest
  /** Re-checks the stored token after `unavailable`. */
  retry: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
