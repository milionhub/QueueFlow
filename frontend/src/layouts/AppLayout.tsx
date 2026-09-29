import type { TFunction } from 'i18next'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Outlet, useLocation, useMatches } from 'react-router'

import { AppHeader, type Crumb } from '../components/app/AppHeader'
import { AppSidebar } from '../components/app/AppSidebar'
import { ToastProvider } from '../components/ui/ToastProvider'
import { CurrentWorkspaceProvider } from '../features/workspace/CurrentWorkspaceProvider'
import { PageTitleContext, type PageTitle } from '../hooks/usePageTitle'
import { ShellProjectContext, type ShellProject } from '../hooks/useShellProject'
import { isFromBoard, projectBoardPath, projectPath, PROJECTS_PATH } from '../routes/paths'

/**
 * What an application route declares about itself, via the route's
 * `handle`: its title, as the key of a message in shell.titles, so the tab
 * follows the interface language.
 */
export interface AppRouteHandle {
  titleKey: 'dashboard' | 'projects' | 'members' | 'notFound'
}

function isAppRouteHandle(handle: unknown): handle is AppRouteHandle {
  return typeof (handle as AppRouteHandle | undefined)?.titleKey === 'string'
}

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * The pages above the current one, from the address alone - so they are
 * right from the first render, before anything has loaded (a ticket's
 * page never says "Projects" while it loads). The project's name replaces
 * its key once the project page reports it. The current page itself is not
 * a crumb: its own heading names it.
 */
function trailFor(t: TFunction<'shell'>, pathname: string, fromBoard: boolean, project: ShellProject | null): Crumb[] {
  const [, app, section, rawKey, view] = pathname.split('/')
  if (app !== 'app' || section !== 'projects' || !rawKey) {
    return []
  }
  let key = rawKey
  try {
    key = decodeURIComponent(rawKey)
  } catch {
    // A malformed address: shown as it is.
  }
  const known = project && project.key.toUpperCase() === key.trim().toUpperCase() ? project : null
  const trail: Crumb[] = [{ label: t('nav.projects'), to: PROJECTS_PATH }]
  if (view === 'board') {
    trail.push({ label: known?.name ?? key.toUpperCase(), to: projectPath(known?.key ?? key) })
  } else if (view === 'tickets') {
    trail.push({ label: known?.name ?? key.toUpperCase(), to: projectPath(known?.key ?? key) })
    if (fromBoard) {
      trail.push({ label: t('board'), to: projectBoardPath(known?.key ?? key) })
    }
  }
  return trail
}

/**
 * The signed-in application: a persistent sidebar on large screens, an
 * off-canvas drawer below `lg`, the header with the breadcrumbs, and the
 * page (Outlet), which carries its own h1. The document title comes from
 * the page (usePageTitle) or else from the deepest route's `handle.title`.
 */
export function AppLayout() {
  const { t } = useTranslation('shell')
  const matches = useMatches()
  const routeTitleKey = [...matches]
    .reverse()
    .map((match) => match.handle)
    .find(isAppRouteHandle)?.titleKey
  const routeTitle = routeTitleKey ? t(`titles.${routeTitleKey}`) : 'QueueFlow'
  const [pageTitle, setPageTitle] = useState<PageTitle | null>(null)
  const [shellProject, setShellProject] = useState<ShellProject | null>(null)
  const documentTitle = pageTitle?.document ?? pageTitle?.heading ?? routeTitle

  // The drawer remembers the page it was opened on, so any navigation -
  // a link, Back, Forward - closes it without an extra render.
  const location = useLocation()
  const [drawerOpenedOn, setDrawerOpenedOn] = useState<string | null>(null)
  const [drawerClosing, setDrawerClosing] = useState(false)
  const navigationOpen = drawerOpenedOn === location.pathname
  const closing = navigationOpen && drawerClosing
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  const drawerRef = useRef<HTMLDivElement>(null)
  const drawerId = useId()

  useEffect(() => {
    document.title = `${documentTitle} · QueueFlow`
  }, [documentTitle])

  const finishClosing = useCallback(() => {
    setDrawerOpenedOn(null)
    setDrawerClosing(false)
  }, [])

  // Closing plays a short slide-out first (none with reduced motion).
  const closeNavigation = useCallback(() => {
    if (prefersReducedMotion()) {
      finishClosing()
    } else {
      setDrawerClosing(true)
    }
  }, [finishClosing])

  useEffect(() => {
    if (!closing) {
      return
    }
    // In case the browser never reports the end of the animation.
    const timer = window.setTimeout(finishClosing, 250)
    return () => window.clearTimeout(timer)
  }, [closing, finishClosing])

  function openNavigation() {
    setDrawerClosing(false)
    setDrawerOpenedOn(location.pathname)
  }

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
    const onChange = () => desktop.matches && finishClosing()
    desktop.addEventListener('change', onChange)
    return () => desktop.removeEventListener('change', onChange)
  }, [finishClosing])

  const trail = trailFor(t, location.pathname, isFromBoard(location.state), shellProject)

  return (
    <CurrentWorkspaceProvider>
      <PageTitleContext value={setPageTitle}>
        <ShellProjectContext value={setShellProject}>
          <ToastProvider>
            <div className="min-h-dvh bg-canvas lg:pl-60">
              <aside
                aria-label={t('sidebar')}
                className="fixed inset-y-0 left-0 hidden w-60 border-r border-line bg-surface lg:block"
              >
                <AppSidebar />
              </aside>

              {navigationOpen && (
                <div className="fixed inset-0 z-40 lg:hidden">
                  <button
                    type="button"
                    tabIndex={-1}
                    aria-label={t('closeNavigation')}
                    onClick={closeNavigation}
                    className={`absolute inset-0 size-full cursor-default bg-ink/35 ${
                      closing ? 'animate-[queueflow-fade-out_150ms_ease-in_both]' : 'animate-fade-in'
                    }`}
                  />
                  <div
                    ref={drawerRef}
                    id={drawerId}
                    role="dialog"
                    aria-modal="true"
                    aria-label={t('drawer')}
                    onAnimationEnd={(event) => {
                      if (closing && event.target === event.currentTarget) {
                        finishClosing()
                      }
                    }}
                    className={`absolute inset-y-0 left-0 w-72 max-w-[85vw] border-r border-line bg-surface shadow-xl ${
                      closing
                        ? 'animate-[queueflow-drawer-out_150ms_ease-in_both]'
                        : 'animate-[queueflow-drawer-in_220ms_var(--ease-snappy)]'
                    }`}
                  >
                    <AppSidebar onNavigate={closeNavigation} onClose={closeNavigation} drawer />
                  </div>
                </div>
              )}

              <div className="flex min-h-dvh min-w-0 flex-col" inert={navigationOpen}>
                <AppHeader
                  trail={trail}
                  navigationOpen={navigationOpen}
                  onOpenNavigation={openNavigation}
                  menuButtonRef={menuButtonRef}
                  navigationId={drawerId}
                />
                <main className="flex-1 px-4 pt-5 pb-10 sm:px-6 sm:pt-7 lg:px-8 lg:pt-8">
                  <Outlet />
                </main>
              </div>
            </div>
          </ToastProvider>
        </ShellProjectContext>
      </PageTitleContext>
    </CurrentWorkspaceProvider>
  )
}
