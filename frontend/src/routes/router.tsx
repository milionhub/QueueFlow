import { createBrowserRouter } from 'react-router'

import { GuestRoute, ProtectedRoute, RootRedirect } from '../features/auth/components/RouteGuards'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { RegisterPage } from '../features/auth/pages/RegisterPage'
import { DashboardPage } from '../features/dashboard/pages/DashboardPage'
import { AppLayout, type AppRouteHandle } from '../layouts/AppLayout'
import { RootLayout } from '../layouts/RootLayout'
import { HealthPage } from '../pages/HealthPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RouteErrorPage } from '../pages/RouteErrorPage'

/**
 * - `/` sends the visitor to /app or /login.
 * - Guest-only pages (sign-in, registration) nest under GuestRoute.
 * - The signed-in application lives under /app: ProtectedRoute, then the
 *   AppLayout shell; /app itself is the dashboard. Feature pages are
 *   added as its children (e.g. `projects`, `projects/:projectId`), each
 *   with a `handle.title`.
 * - /health and the public 404 page are open to everyone.
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
        path: 'app',
        element: <ProtectedRoute />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { index: true, element: <DashboardPage />, handle: { title: 'Dashboard' } satisfies AppRouteHandle },
              { path: '*', element: <NotFoundPage />, handle: { title: 'Page not found' } satisfies AppRouteHandle },
            ],
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
