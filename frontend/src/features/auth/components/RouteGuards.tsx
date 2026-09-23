import { Navigate, Outlet, useLocation, type Location } from 'react-router'

import { useAuth } from '../useAuth'
import { SessionLoadingScreen, SessionUnavailableScreen } from './SessionScreens'

/** Where an unauthenticated visitor was going, kept in the router's location state. */
interface RedirectState {
  from?: Pick<Location, 'pathname' | 'search' | 'hash'>
}

const HOME = '/app'

/**
 * Parent route for everything that needs a signed-in user. Nothing is
 * rendered until the session is known, so protected content never flashes.
 * Authorization itself stays with the backend; this only decides what to show.
 */
export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  switch (status) {
    case 'checking':
      return <SessionLoadingScreen />
    case 'unavailable':
      return <SessionUnavailableScreen />
    case 'unauthenticated': {
      const state: RedirectState = {
        from: { pathname: location.pathname, search: location.search, hash: location.hash },
      }
      return <Navigate to="/login" replace state={state} />
    }
    case 'authenticated':
      return <Outlet />
  }
}

/**
 * Parent route for /login and /register. A signed-in user is sent on - to
 * the page they originally asked for, or /app - which is also what happens
 * right after a successful sign-in or registration.
 */
export function GuestRoute() {
  const { status } = useAuth()
  const location = useLocation()

  switch (status) {
    case 'checking':
      return <SessionLoadingScreen />
    case 'unavailable':
      return <SessionUnavailableScreen />
    case 'authenticated':
      return <Navigate to={destination(location.state)} replace />
    case 'unauthenticated':
      return <Outlet />
  }
}

/** `/` has no page of its own: it goes to the app or to sign-in. */
export function RootRedirect() {
  const { status } = useAuth()

  switch (status) {
    case 'checking':
      return <SessionLoadingScreen />
    case 'unavailable':
      return <SessionUnavailableScreen />
    case 'authenticated':
      return <Navigate to={HOME} replace />
    case 'unauthenticated':
      return <Navigate to="/login" replace />
  }
}

/** Only an in-app path the router itself recorded; anything else falls back to /app. */
function destination(state: unknown): string {
  const from = (state as RedirectState | null)?.from
  if (from && typeof from.pathname === 'string' && from.pathname.startsWith('/') && !from.pathname.startsWith('//')) {
    return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
  }
  return HOME
}
