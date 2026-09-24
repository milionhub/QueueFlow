import { ApiError } from '../../api/errors'
import { fieldValidationErrors, genericErrorMessage, type FormErrors } from '../../lib/formErrors'

const TICKET_FIELD_LABELS = {
  title: 'Title',
  description: 'Description',
  status: 'Status',
  priority: 'Priority',
  assigneeId: 'Assignee',
} as const

export type TicketField = keyof typeof TICKET_FIELD_LABELS

/**
 * A failed ticket create, mapped to what the user can act on. The backend
 * answers 404 for the project or for the assignee; its message says which
 * ("Project not found: …" / "User not found: …").
 */
export function createTicketErrors(error: unknown): FormErrors<TicketField> {
  if (error instanceof ApiError && error.kind === 'http') {
    if (error.status === 400 && error.body) {
      return fieldValidationErrors(error.body.message, TICKET_FIELD_LABELS)
    }
    if (error.status === 404) {
      return error.body?.message.startsWith('User not found')
        ? { form: null, fields: { assigneeId: 'That person is no longer in this workspace.' } }
        : { form: 'This project is no longer available.', fields: {} }
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
      return { field: 'assigneeId', message: 'That person is no longer in this workspace.' }
    }
    if (notFound.startsWith('Label not found')) {
      return { field: null, message: 'That label is no longer available.' }
    }
    return { field: null, message: 'This ticket is no longer available.' }
  }
  if (error instanceof ApiError && error.kind === 'http' && error.status === 400 && error.body) {
    const mapped = fieldValidationErrors(error.body.message, TICKET_FIELD_LABELS)
    const [field, message] = Object.entries(mapped.fields)[0] ?? []
    if (field && message) {
      return { field: field as TicketField, message }
    }
    return { field: null, message: 'That change is not valid.' }
  }
  return { field: null, message: genericErrorMessage(error) }
}
