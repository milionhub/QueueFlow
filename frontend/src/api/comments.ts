import type { AuthorizedRequest } from './client'

/**
 * The backend's CommentResponse. Plain text: never rendered as Markdown or
 * HTML. createdAt and updatedAt are equal until the content really changes.
 */
export interface Comment {
  id: string
  content: string
  ticketId: string
  /** Always the user who wrote it: only they may edit or delete it (ADMIN included). */
  authorId: string
  authorName: string
  createdAt: string
  updatedAt: string
}

/** The ticket's comments, oldest first - keep that order. Not paginated. */
export function listTicketComments(
  request: AuthorizedRequest,
  ticketId: string,
  signal?: AbortSignal,
): Promise<Comment[]> {
  return request<Comment[]>(`/api/tickets/${encodeURIComponent(ticketId)}/comments`, { signal })
}

/**
 * Any member may comment; the author is always the caller. The backend
 * does not trim the content (send it trimmed) and rejects a blank one (400).
 */
export function createComment(request: AuthorizedRequest, ticketId: string, content: string): Promise<Comment> {
  return request<Comment>('/api/comments', { method: 'POST', body: { ticketId, content } })
}

/** Only the author may edit (403 otherwise). Returns the comment with its new updatedAt. */
export function updateComment(request: AuthorizedRequest, commentId: string, content: string): Promise<Comment> {
  return request<Comment>(`/api/comments/${encodeURIComponent(commentId)}`, { method: 'PATCH', body: { content } })
}

/** Only the author may delete (403 otherwise). 204: resolves with nothing. */
export function deleteComment(request: AuthorizedRequest, commentId: string): Promise<void> {
  return request<void>(`/api/comments/${encodeURIComponent(commentId)}`, { method: 'DELETE' })
}
