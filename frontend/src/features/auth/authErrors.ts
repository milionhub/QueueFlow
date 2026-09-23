import { ApiError } from '../../api/errors'

/** A failed sign-in or registration, ready to show: field messages next to their inputs, the rest above the form. */
export interface FormErrors<Field extends string> {
  form: string | null
  fields: Partial<Record<Field, string>>
}

const UNREACHABLE = "QueueFlow couldn't reach the server. Please try again."
const UNEXPECTED = 'Something went wrong. Please try again.'

/** One fixed message for any rejected credentials: it never says whether the email exists. */
export function loginErrors(error: unknown): FormErrors<never> {
  if (error instanceof ApiError && error.kind === 'http' && error.status === 401) {
    return { form: 'Invalid email or password.', fields: {} }
  }
  return { form: genericMessage(error), fields: {} }
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
      return validationErrors(error.body.message)
    }
  }
  return { form: genericMessage(error), fields: {} }
}

/**
 * The backend reports validation as "<field> <problem>" messages joined by
 * "; " (e.g. "password must be at least 8 characters"). Each is shown under
 * its field, with the field's label; anything else goes above the form.
 */
function validationErrors(message: string): FormErrors<RegisterField> {
  const result: FormErrors<RegisterField> = { form: null, fields: {} }
  const unmatched: string[] = []
  for (const part of message.split('; ')) {
    const [field, ...rest] = part.split(' ')
    if (field in REGISTER_FIELD_LABELS && rest.length > 0) {
      const key = field as RegisterField
      result.fields[key] ??= `${REGISTER_FIELD_LABELS[key]} ${rest.join(' ')}.`
    } else {
      unmatched.push(part)
    }
  }
  if (unmatched.length > 0) {
    result.form = 'Please check the form and try again.'
  }
  return result
}

function genericMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.kind === 'network') {
      return UNREACHABLE
    }
    if (error.kind === 'configuration') {
      return 'QueueFlow is not configured correctly. Please contact the administrator.'
    }
  }
  return UNEXPECTED
}
