import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Outlet, useLocation, useMatches } from 'react-router'

import { AppHeader } from '../components/app/AppHeader'
import { AppSidebar } from '../components/app/AppSidebar'
import { CurrentWorkspaceProvider } from '../features/workspace/CurrentWorkspaceProvider'
import { PageTitleContext, type PageTitle } from '../hooks/usePageTitle'

/** What an application route declares about itself, via the route's `handle`. */
export interface AppRouteHandle {
  title: string
}

function isAppRouteHandle(handle: unknown): handle is AppRouteHandle {
  return typeof (handle as AppRouteHandle | undefined)?.title === 'string'
}

/**
 * The signed-in application: a persistent sidebar on large screens, an
 * off-canvas drawer below `lg`, the header, and the page (Outlet). The page
 * title comes from the deepest matching route's `handle.title` - or from
 * the page itself (usePageTitle), e.g. a project's name - and is also the
 * document title.
 */
export function AppLayout() {
  const matches = useMatches()
  const routeTitle = [...matches].reverse().map((match) => match.handle).find(isAppRouteHandle)?.title ?? 'QueueFlow'
  const [pageTitle, setPageTitle] = useState<PageTitle | null>(null)
  const title = pageTitle?.heading ?? routeTitle
  const documentTitle = pageTitle?.document ?? title

  // The drawer remembers the page it was opened on, so any navigation -
  // a link, Back, Forward - closes it without an extra render.
  const location = useLocation()
  const [drawerOpenedOn, setDrawerOpenedOn] = useState<string | null>(null)
  const navigationOpen = drawerOpenedOn === location.pathname
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  const drawerRef = useRef<HTMLDivElement>(null)
  const drawerId = useId()

  useEffect(() => {
    document.title = `${documentTitle} · QueueFlow`
  }, [documentTitle])

  const closeNavigation = useCallback(() => setDrawerOpenedOn(null), [])

  // While the drawer is open: Escape closes it, the page behind does not
  // scroll, and focus moves into the drawer (the rest of the page is inert).
  // Closing returns focus to the menu button - in the cleanup, which runs
  // once the page is no longer inert, so the focus call is not ignored.
  useEffect(() => {
    if (!navigationOpen) {
      return
    }
    const menuButton = menuButtonRef.current
    const { overflow } = document.body.style
    document.body.style.overflow = 'hidden'
    drawerRef.current?.querySelector<HTMLElement>('button, a')?.focus()
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        closeNavigation()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = overflow
      document.removeEventListener('keydown', onKeyDown)
      menuButton?.focus()
    }
  }, [navigationOpen, closeNavigation])

  // The drawer is only for small screens: widening the window closes it.
  useEffect(() => {
    const desktop = window.matchMedia('(min-width: 64rem)')
    const onChange = () => desktop.matches && setDrawerOpenedOn(null)
    desktop.addEventListener('change', onChange)
    return () => desktop.removeEventListener('change', onChange)
  }, [])

  return (
    <CurrentWorkspaceProvider>
      <PageTitleContext value={setPageTitle}>
        <div className="min-h-dvh bg-surface lg:pl-60">
          <aside aria-label="Sidebar" className="fixed inset-y-0 left-0 hidden w-60 border-r border-line lg:block">
            <AppSidebar />
          </aside>

          {navigationOpen && (
            <div className="fixed inset-0 z-40 lg:hidden">
              <button
                type="button"
                tabIndex={-1}
                aria-label="Close navigation"
                onClick={closeNavigation}
                className="absolute inset-0 size-full cursor-default bg-ink/30"
              />
              <div
                ref={drawerRef}
                id={drawerId}
                role="dialog"
                aria-modal="true"
                aria-label="Navigation"
                className="absolute inset-y-0 left-0 w-72 max-w-[85vw] border-r border-line shadow-xl motion-safe:animate-[queueflow-drawer-in_160ms_ease-out]"
              >
                <AppSidebar onNavigate={closeNavigation} onClose={closeNavigation} />
              </div>
            </div>
          )}

          <div className="flex min-h-dvh min-w-0 flex-col" inert={navigationOpen}>
            <AppHeader
              title={title}
              navigationOpen={navigationOpen}
              onOpenNavigation={() => setDrawerOpenedOn(location.pathname)}
              menuButtonRef={menuButtonRef}
              navigationId={drawerId}
            />
            <main className="flex-1 px-4 py-8 sm:px-6 lg:px-8">
              <Outlet />
            </main>
          </div>
        </div>
      </PageTitleContext>
    </CurrentWorkspaceProvider>
  )
}
