import { ApiError } from '../../api/errors'
import { i18n } from '../../i18n'
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
      return i18n.t('tickets:comments.errors.empty')
    case 403:
      if (action === 'add') {
        return i18n.t('tickets:comments.errors.notAllowed')
      }
      return action === 'edit'
        ? i18n.t('tickets:comments.errors.onlyAuthorEdit')
        : i18n.t('tickets:comments.errors.onlyAuthorDelete')
    case 404:
      return isCommentGone(error) ? i18n.t('tickets:comments.errors.gone') : i18n.t('tickets:errors.ticketGone')
    default:
      return genericErrorMessage(error)
  }
}
