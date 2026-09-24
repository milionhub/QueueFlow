import { createContext, useContext, useEffect } from 'react'

/**
 * A title a page sets for itself (e.g. a project's name), replacing its
 * route's static `handle.title` in the shell header. `document` is the
 * browser tab title, when it should say more than the header.
 */
export interface PageTitle {
  heading: string
  document?: string
}

export const PageTitleContext = createContext<((title: PageTitle | null) => void) | null>(null)

/** Sets the header and tab title while the page is shown; null keeps the route's own title. */
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
