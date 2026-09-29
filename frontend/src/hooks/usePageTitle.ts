import { createContext, useContext, useEffect } from 'react'

/**
 * A title a page sets for itself (e.g. a project's name), replacing its
 * route's static `handle.title` as the browser tab's title. `document`,
 * when given, is used instead of `heading` for the tab. (The page's own
 * PageHeader shows its heading; the shell shows breadcrumbs.)
 */
export interface PageTitle {
  heading: string
  document?: string
}

export const PageTitleContext = createContext<((title: PageTitle | null) => void) | null>(null)

/** Sets the tab title while the page is shown; null keeps the route's own title. */
export function usePageTitle(heading: string | null, documentTitle?: string) {
  const setPageTitle = useContext(PageTitleContext)
  useEffect(() => {
    if (!setPageTitle || heading === null) {
      return
    }
    setPageTitle({ heading, document: documentTitle })
    return () => setPageTitle(null)
  }, [setPageTitle, heading, documentTitle])
}
