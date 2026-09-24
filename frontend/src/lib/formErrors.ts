import { ApiError } from '../api/errors'

/** A failed submission, ready to show: field messages next to their inputs, the rest above the form. */
export interface FormErrors<Field extends string> {
  form: string | null
  fields: Partial<Record<Field, string>>
}

const UNREACHABLE = "QueueFlow couldn't reach the server. Please try again."
const UNEXPECTED = 'Something went wrong. Please try again.'

/**
 * The backend reports validation as "<field> <problem>" messages joined by
 * "; " (e.g. "name must not be blank"). Each is shown under its field, with
 * the field's label; anything else is summarized above the form.
 */
export function fieldValidationErrors<Field extends string>(
  message: string,
  labels: Record<Field, string>,
): FormErrors<Field> {
  const result: FormErrors<Field> = { form: null, fields: {} }
  const unmatched: string[] = []
  for (const part of message.split('; ')) {
    const [field, ...rest] = part.split(' ')
    if (field in labels && rest.length > 0) {
      const key = field as Field
      result.fields[key] ??= `${labels[key]} ${rest.join(' ')}.`
    } else {
      unmatched.push(part)
    }
  }
  if (unmatched.length > 0) {
    result.form = 'Please check the form and try again.'
  }
  return result
}

/** For failures that are not about the submitted values: no connection, bad configuration, anything unexpected. */
export function genericErrorMessage(error: unknown): string {
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
