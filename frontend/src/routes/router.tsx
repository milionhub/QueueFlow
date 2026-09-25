import { createBrowserRouter } from 'react-router'

import { GuestRoute, ProtectedRoute, RootRedirect } from '../features/auth/components/RouteGuards'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { RegisterPage } from '../features/auth/pages/RegisterPage'
import { DashboardPage } from '../features/dashboard/pages/DashboardPage'
import { MembersPage } from '../features/members/pages/MembersPage'
import { ProjectLayout, ProjectSkeleton } from '../features/projects/ProjectLayout'
import { ProjectsPage } from '../features/projects/pages/ProjectsPage'
import { ProjectTicketsPage } from '../features/tickets/pages/ProjectTicketsPage'
import { TicketDetailPage } from '../features/tickets/pages/TicketDetailPage'
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
 *   added as its children, each with a `handle.title`. A project's pages
 *   (/app/projects/:projectKey, .../board, ...) share ProjectLayout, which loads the
 *   project and the workspace's members once for all of them.
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
              { path: 'projects', element: <ProjectsPage />, handle: { title: 'Projects' } satisfies AppRouteHandle },
              {
                // A project's pages; each sets its own title once the project has loaded.
                path: 'projects/:projectKey',
                element: <ProjectLayout />,
                // Shown inside the shell while the board's code loads on a direct visit.
                hydrateFallbackElement: <ProjectSkeleton />,
                handle: { title: 'Projects' } satisfies AppRouteHandle,
                children: [
                  { index: true, element: <ProjectTicketsPage /> },
                  {
                    // Loaded when first visited: drag and drop is only needed here.
                    path: 'board',
                    lazy: () =>
                      import('../features/board/pages/ProjectBoardPage').then(({ ProjectBoardPage }) => ({
                        Component: ProjectBoardPage,
                      })),
                  },
                  { path: 'tickets/:ticketNumber', element: <TicketDetailPage /> },
                ],
              },
              { path: 'members', element: <MembersPage />, handle: { title: 'Members' } satisfies AppRouteHandle },
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
