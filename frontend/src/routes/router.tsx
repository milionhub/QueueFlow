import { createBrowserRouter } from 'react-router'

import { RootLayout } from '../layouts/RootLayout'
import { FoundationPage } from '../pages/FoundationPage'
import { HealthPage } from '../pages/HealthPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RouteErrorPage } from '../pages/RouteErrorPage'

/**
 * All routes, in one tree. Later phases add public pages (login, register)
 * next to these and nest the authenticated application under a guard
 * layout route, without changing this structure.
 */
export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <FoundationPage /> },
      { path: 'health', element: <HealthPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
