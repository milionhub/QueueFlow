import { useEffect } from 'react'

/** Sets the browser tab title to "<title> · QueueFlow" while the page is shown. */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · QueueFlow`
  }, [title])
}
