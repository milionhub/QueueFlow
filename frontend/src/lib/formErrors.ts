import { ApiError } from '../api/errors'
import { i18n } from '../i18n'

/** A failed submission, ready to show: field messages next to their inputs, the rest above the form. */
export interface FormErrors<Field extends string> {
  form: string | null
  fields: Partial<Record<Field, string>>
}

/**
 * One problem from a backend validation message ("must not be blank"),
 * in the interface language. The backend words its messages in English
 * only (UserAccountRules and the request DTOs' constraints), so the known
 * wordings are matched here; any other is shown as the backend put it,
 * after the field's label.
 */
function describeProblem(label: string, problem: string): string {
  if (problem === 'must not be blank') {
    return i18n.t('common:validation.blank', { field: label })
  }
  if (problem === 'is required' || problem === 'must not be null') {
    return i18n.t('common:validation.required', { field: label })
  }
  const maxCharacters = /^must be at most (\d+) characters$/.exec(problem)
  if (maxCharacters) {
    return i18n.t('common:validation.maxCharacters', { field: label, max: Number(maxCharacters[1]) })
  }
  const minCharacters = /^must be at least (\d+) characters$/.exec(problem)
  if (minCharacters) {
    return i18n.t('common:validation.minCharacters', { field: label, min: Number(minCharacters[1]) })
  }
  const maxBytes = /^must be at most (\d+) bytes when UTF-8 encoded$/.exec(problem)
  if (maxBytes) {
    return i18n.t('common:validation.maxBytes', { field: label, max: Number(maxBytes[1]) })
  }
  if (problem === 'must be a valid email address') {
    return i18n.t('common:validation.email', { field: label })
  }
  if (problem.startsWith('must be 2-10 characters, using only letters A-Z and digits 0-9')) {
    return i18n.t('common:validation.keyFormat', { field: label })
  }
  return i18n.t('common:validation.unknown', { field: label, problem })
}

/**
 * The backend reports validation as "<field> <problem>" messages joined by
 * "; " (e.g. "name must not be blank"). Each is shown under its field, with
 * the field's label (already in the interface language); anything else is
 * summarized above the form.
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
      result.fields[key] ??= describeProblem(labels[key], rest.join(' '))
    } else {
      unmatched.push(part)
    }
  }
  if (unmatched.length > 0) {
    result.form = i18n.t('common:errors.checkForm')
  }
  return result
}

/** For failures that are not about the submitted values: no connection, bad configuration, anything unexpected. */
export function genericErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.kind === 'network') {
      return i18n.t('common:errors.unreachable')
    }
    if (error.kind === 'configuration') {
      return i18n.t('common:errors.notConfigured')
    }
  }
  return i18n.t('common:errors.unexpected')
}
