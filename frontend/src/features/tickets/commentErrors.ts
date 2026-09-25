import { ApiError } from '../../api/errors'
import { genericErrorMessage } from '../../lib/formErrors'

function httpStatus(error: unknown): number | null {
  return error instanceof ApiError && error.kind === 'http' ? error.status : null
}

/** The comment itself no longer exists (deleted elsewhere), as opposed to its ticket. */
export function isCommentGone(error: unknown): boolean {
  return httpStatus(error) === 404 && (error as ApiError).body?.message.startsWith('Comment not found') === true
}

/**
 * A failed comment change, as one sentence for an inline error. The
 * backend decides who may edit or delete; its refusal is shown, never
 * hidden.
 */
export function commentChangeError(error: unknown, action: 'add' | 'edit' | 'delete'): string {
  switch (httpStatus(error)) {
    case 400:
      // The backend's only rule for the content: it must not be blank.
      return 'A comment cannot be empty.'
    case 403:
      return action === 'add' ? 'You are not allowed to comment here.' : `Only the author can ${action} this comment.`
    case 404:
      return isCommentGone(error) ? 'This comment no longer exists.' : 'This ticket is no longer available.'
    default:
      return genericErrorMessage(error)
  }
}
