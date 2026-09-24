import { ApiError } from '../../api/errors'
import { fieldValidationErrors, genericErrorMessage, type FormErrors } from '../../lib/formErrors'

export type { FormErrors } from '../../lib/formErrors'

/** One fixed message for any rejected credentials: it never says whether the email exists. */
export function loginErrors(error: unknown): FormErrors<never> {
  if (error instanceof ApiError && error.kind === 'http' && error.status === 401) {
    return { form: 'Invalid email or password.', fields: {} }
  }
  return { form: genericErrorMessage(error), fields: {} }
}

const REGISTER_FIELD_LABELS = {
  name: 'Name',
  email: 'Email',
  password: 'Password',
  workspaceName: 'Workspace name',
} as const

export type RegisterField = keyof typeof REGISTER_FIELD_LABELS

export function registerErrors(error: unknown): FormErrors<RegisterField> {
  if (error instanceof ApiError && error.kind === 'http') {
    if (error.status === 409) {
      return { form: 'An account with that email already exists. Sign in instead?', fields: {} }
    }
    if (error.status === 400 && error.body) {
      return fieldValidationErrors(error.body.message, REGISTER_FIELD_LABELS)
    }
  }
  return { form: genericErrorMessage(error), fields: {} }
}
