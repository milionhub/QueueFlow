import { createBrowserRouter } from 'react-router'

import { GuestRoute, ProtectedRoute, RootRedirect } from '../features/auth/components/RouteGuards'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { RegisterPage } from '../features/auth/pages/RegisterPage'
import { RootLayout } from '../layouts/RootLayout'
import { AppHomePage } from '../pages/AppHomePage'
import { HealthPage } from '../pages/HealthPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RouteErrorPage } from '../pages/RouteErrorPage'

/**
 * - `/` sends the visitor to /app or /login.
 * - Guest-only pages (sign-in, registration) nest under GuestRoute.
 * - Everything that needs a signed-in user nests under ProtectedRoute;
 *   later pages are added as its children.
 * - /health and the 404 page are open to everyone.
 */
export const router = createBrowserRouter([
  {
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <RootRedirect /> },
      {
        element: <GuestRoute />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
        ],
      },
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <RootLayout />,
            children: [{ path: 'app', element: <AppHomePage /> }],
          },
        ],
      },
      {
        element: <RootLayout />,
        children: [
          { path: 'health', element: <HealthPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
