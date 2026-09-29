import { ApiError } from '../../api/errors'
import { i18n } from '../../i18n'
import { fieldValidationErrors, genericErrorMessage, type FormErrors } from '../../lib/formErrors'

export type { FormErrors } from '../../lib/formErrors'

/** One fixed message for any rejected credentials: it never says whether the email exists. */
export function loginErrors(error: unknown): FormErrors<never> {
  if (error instanceof ApiError && error.kind === 'http' && error.status === 401) {
    return { form: i18n.t('auth:login.invalidCredentials'), fields: {} }
  }
  return { form: genericErrorMessage(error), fields: {} }
}

export type RegisterField = 'name' | 'email' | 'password' | 'workspaceName'

/** The registration fields' names as the backend's messages use them, with their labels in the interface language. */
function registerFieldLabels(): Record<RegisterField, string> {
  return {
    name: i18n.t('auth:fields.name'),
    email: i18n.t('auth:fields.email'),
    password: i18n.t('auth:fields.password'),
    workspaceName: i18n.t('auth:fields.workspaceName'),
  }
}

export function registerErrors(error: unknown): FormErrors<RegisterField> {
  if (error instanceof ApiError && error.kind === 'http') {
    if (error.status === 409) {
      return { form: i18n.t('auth:register.emailTaken'), fields: {} }
    }
    if (error.status === 400 && error.body) {
      return fieldValidationErrors(error.body.message, registerFieldLabels())
    }
  }
  return { form: genericErrorMessage(error), fields: {} }
}
