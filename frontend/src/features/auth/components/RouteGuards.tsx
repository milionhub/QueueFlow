import { Navigate, Outlet } from 'react-router'

import { useAuth } from '../useAuth'
import { SessionLoadingScreen, SessionUnavailableScreen } from './SessionScreens'

/** Where every sign-in and registration lands: the signed-in user's dashboard. */
const HOME = '/app'

/**
 * Parent route for everything that needs a signed-in user. Nothing is
 * rendered until the session is known, so protected content never flashes.
 * Authorization itself stays with the backend; this only decides what to show.
 */
export function ProtectedRoute() {
  const { status } = useAuth()

  switch (status) {
    case 'checking':
      return <SessionLoadingScreen />
    case 'unavailable':
      return <SessionUnavailableScreen />
    case 'unauthenticated':
      return <Navigate to="/login" replace />
    case 'authenticated':
      return <Outlet />
  }
}

/**
 * Parent route for /login and /register. A signed-in user is sent to /app,
 * which is also what happens right after a successful sign-in or
 * registration. Deliberately never to the page that was open before: after
 * a sign-out that page belonged to the previous session - possibly another
 * user's ticket or project - so every session starts from its own
 * dashboard.
 */
export function GuestRoute() {
  const { status } = useAuth()

  switch (status) {
    case 'checking':
      return <SessionLoadingScreen />
    case 'unavailable':
      return <SessionUnavailableScreen />
    case 'authenticated':
      return <Navigate to={HOME} replace />
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
