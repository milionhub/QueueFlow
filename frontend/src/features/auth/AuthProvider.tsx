import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import * as authApi from '../../api/auth'
import { ApiError } from '../../api/errors'
import { AuthContext, type AuthContextValue, type AuthStatus, type SignOutReason } from './authContext'
import { tokenStorage } from './tokenStorage'
import type { AuthResponse, CurrentUser } from './types'

interface AuthState {
  status: AuthStatus
  user: CurrentUser | null
  signOutReason: SignOutReason | null
}

/**
 * Owns the session: the token (through tokenStorage) and the current user,
 * which always comes from the backend - from login/register's AuthResponse
 * or from GET /api/auth/me - never from the token's contents.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => ({
    status: tokenStorage.get() ? 'checking' : 'unauthenticated',
    user: null,
    signOutReason: null,
  }))
  const [bootstrapAttempt, setBootstrapAttempt] = useState(0)

  // Bootstrap: verify a stored token with /me. Only a 401 means the token
  // is no good; anything else (network, 5xx) keeps it and reports unavailable.
  useEffect(() => {
    const token = tokenStorage.get()
    if (!token) {
      return
    }
    const controller = new AbortController()
    authApi
      .getCurrentUser(token, controller.signal)
      .then((user) => setState({ status: 'authenticated', user, signOutReason: null }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        if (error instanceof ApiError && error.kind === 'http' && error.status === 401) {
          tokenStorage.clear()
          setState({ status: 'unauthenticated', user: null, signOutReason: 'session-expired' })
        } else {
          setState({ status: 'unavailable', user: null, signOutReason: null })
        }
      })
    return () => controller.abort()
  }, [bootstrapAttempt])

  // AuthResponse.user is the same UserResponse that /me returns, fresh
  // from the database, so no extra /me call is needed after signing in.
  const startSession = useCallback((response: AuthResponse) => {
    tokenStorage.set(response.accessToken)
    setState({ status: 'authenticated', user: response.user, signOutReason: null })
  }, [])

  const login = useCallback<AuthContextValue['login']>(
    async (request) => startSession(await authApi.login(request)),
    [startSession],
  )

  const register = useCallback<AuthContextValue['register']>(
    async (request) => startSession(await authApi.register(request)),
    [startSession],
  )

  const logout = useCallback(() => {
    tokenStorage.clear()
    setState({ status: 'unauthenticated', user: null, signOutReason: null })
  }, [])

  const expireSession = useCallback(() => {
    tokenStorage.clear()
    setState({ status: 'unauthenticated', user: null, signOutReason: 'session-expired' })
  }, [])

  const retry = useCallback(() => {
    setState((current) => ({ ...current, status: tokenStorage.get() ? 'checking' : 'unauthenticated' }))
    setBootstrapAttempt((attempt) => attempt + 1)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ ...state, login, register, logout, expireSession, retry }),
    [state, login, register, logout, expireSession, retry],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
