import { ApiError } from '../../api/errors'
import { i18n } from '../../i18n'
import { fieldValidationErrors, genericErrorMessage, type FormErrors } from '../../lib/formErrors'

export type TicketField = 'title' | 'description' | 'status' | 'priority' | 'assigneeId'

/** The ticket fields' names as the backend's messages use them, with their labels in the interface language. */
function ticketFieldLabels(): Record<TicketField, string> {
  return {
    title: i18n.t('tickets:fields.title'),
    description: i18n.t('tickets:fields.description'),
    status: i18n.t('tickets:fields.status'),
    priority: i18n.t('tickets:fields.priority'),
    assigneeId: i18n.t('tickets:fields.assignee'),
  }
}

/**
 * A failed ticket create, mapped to what the user can act on. The backend
 * answers 404 for the project or for the assignee; its message says which
 * ("Project not found: …" / "User not found: …") - only told apart here,
 * never shown.
 */
export function createTicketErrors(error: unknown): FormErrors<TicketField> {
  if (error instanceof ApiError && error.kind === 'http') {
    if (error.status === 400 && error.body) {
      return fieldValidationErrors(error.body.message, ticketFieldLabels())
    }
    if (error.status === 404) {
      return error.body?.message.startsWith('User not found')
        ? { form: null, fields: { assigneeId: i18n.t('tickets:errors.assigneeGone') } }
        : { form: i18n.t('tickets:errors.projectGone'), fields: {} }
    }
  }
  return { form: genericErrorMessage(error), fields: {} }
}

function notFoundMessage(error: unknown): string | null {
  return error instanceof ApiError && error.kind === 'http' && error.status === 404 ? (error.body?.message ?? '') : null
}

/** The ticket itself no longer exists for this user (as opposed to its assignee or a label). */
export function isTicketGone(error: unknown): boolean {
  return notFoundMessage(error)?.startsWith('Ticket not found') ?? false
}

/**
 * A failed change on the ticket page, as one sentence for an inline error.
 * `field` is set when the backend rejected a value of that field (400).
 */
export function ticketChangeError(error: unknown): { field: TicketField | null; message: string } {
  const notFound = notFoundMessage(error)
  if (notFound !== null) {
    if (notFound.startsWith('User not found')) {
      return { field: 'assigneeId', message: i18n.t('tickets:errors.assigneeGone') }
    }
    if (notFound.startsWith('Label not found')) {
      return { field: null, message: i18n.t('tickets:errors.labelGone') }
    }
    return { field: null, message: i18n.t('tickets:errors.ticketGone') }
  }
  if (error instanceof ApiError && error.kind === 'http' && error.status === 400 && error.body) {
    const mapped = fieldValidationErrors(error.body.message, ticketFieldLabels())
    const [field, message] = Object.entries(mapped.fields)[0] ?? []
    if (field && message) {
      return { field: field as TicketField, message }
    }
    return { field: null, message: i18n.t('tickets:errors.invalidChange') }
  }
  return { field: null, message: genericErrorMessage(error) }
}
