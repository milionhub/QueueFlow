import { useCallback, useEffect, useRef } from 'react'

import { listTicketComments, type Comment } from '../../api/comments'
import { useResource, type ResourceState } from '../../lib/useResource'
import { useAuth } from '../auth/useAuth'

export interface TicketComments {
  state: ResourceState<Comment[]>
  retry: () => void
  /** Adds a comment the backend created, in the backend's order (oldest first). */
  added: (comment: Comment) => void
  /** Swaps in the comment the backend returned after an edit. */
  updated: (comment: Comment) => void
  /** Drops a comment the backend deleted (or no longer has). */
  removed: (commentId: string) => void
}

/** As instants: the backend's timestamps need not have the same number of fraction digits. */
function byCreation(a: Comment, b: Comment): number {
  return Date.parse(a.createdAt) - Date.parse(b.createdAt)
}

/**
 * The ticket's comments, loaded once; every change afterwards applies the
 * backend's answer to the list instead of loading it again. Changes of
 * different comments may finish in any order: each applies to the latest
 * list, so none undoes another.
 */
export function useTicketComments(ticketId: string): TicketComments {
  const { authorizedRequest } = useAuth()
  const { state, retry, replace } = useResource(`comments:${ticketId}`, (signal) =>
    listTicketComments(authorizedRequest, ticketId, signal),
  )

  const current = useRef<Comment[] | null>(null)
  const data = state.status === 'ready' ? state.data : null
  useEffect(() => {
    current.current = data
  }, [data])

  const apply = useCallback(
    (change: (comments: Comment[]) => Comment[]) => {
      if (current.current === null) {
        return
      }
      current.current = change(current.current)
      replace(current.current)
    },
    [replace],
  )

  const added = useCallback(
    (comment: Comment) =>
      apply((comments) =>
        comments.some((existing) => existing.id === comment.id)
          ? comments
          : // Stable sort: a comment created in the same instant as another stays after it.
            [...comments, comment].sort(byCreation),
      ),
    [apply],
  )
  const updated = useCallback(
    (comment: Comment) =>
      apply((comments) => comments.map((existing) => (existing.id === comment.id ? comment : existing))),
    [apply],
  )
  const removed = useCallback(
    (commentId: string) => apply((comments) => comments.filter((existing) => existing.id !== commentId)),
    [apply],
  )

  return { state, retry, added, updated, removed }
}
