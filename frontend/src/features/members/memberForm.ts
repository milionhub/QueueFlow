import { ApiError } from '../../api/errors'
import { fieldValidationErrors, genericErrorMessage, type FormErrors } from '../../lib/formErrors'

/** The Add member form. `confirmPassword` never leaves the browser. */
export interface MemberFormValues {
  name: string
  email: string
  password: string
  confirmPassword: string
}

export type MemberField = keyof MemberFormValues

// The backend's account rules (UserAccountRules), checked here first so most
// mistakes are caught before a request; the backend stays the authority.
export const NAME_MAX_LENGTH = 255
export const EMAIL_MAX_LENGTH = 320
const PASSWORD_MIN_CHARACTERS = 8
const PASSWORD_MAX_UTF8_BYTES = 72
/** The backend's deliberately permissive syntax: one "@", a dotted domain, no whitespace. */
const EMAIL_SYNTAX = /^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/

export const PASSWORD_HINT =
  'At least 8 characters. Very long passwords are limited to 72 bytes (fewer characters if they include accents or emoji).'

const BACKEND_FIELD_LABELS = { name: 'Name', email: 'Email', password: 'Password' } as const

/**
 * Field messages for what can be told without the backend. Names and emails
 * are checked trimmed (the backend trims them); passwords exactly as typed.
 */
export function validateMember(values: MemberFormValues): FormErrors<MemberField> {
  const fields: Partial<Record<MemberField, string>> = {}

  const name = values.name.trim()
  if (name === '') {
    fields.name = 'Enter a name.'
  } else if (name.length > NAME_MAX_LENGTH) {
    fields.name = `Name must be at most ${NAME_MAX_LENGTH} characters.`
  }

  const email = values.email.trim()
  if (email === '') {
    fields.email = 'Enter an email address.'
  } else if (email.length > EMAIL_MAX_LENGTH || !EMAIL_SYNTAX.test(email.toLowerCase())) {
    fields.email = 'Enter a valid email address, e.g. name@example.com.'
  }

  const { password, confirmPassword } = values
  if (password === '') {
    fields.password = 'Enter a password.'
  } else if ([...password].length < PASSWORD_MIN_CHARACTERS) {
    fields.password = `Password must be at least ${PASSWORD_MIN_CHARACTERS} characters.`
  } else if (new TextEncoder().encode(password).length > PASSWORD_MAX_UTF8_BYTES) {
    fields.password = `Password must be at most ${PASSWORD_MAX_UTF8_BYTES} bytes: use fewer characters, or fewer accents and emoji.`
  }

  if (confirmPassword === '') {
    fields.confirmPassword = 'Enter the password again.'
  } else if (confirmPassword !== password) {
    fields.confirmPassword = "The passwords don't match."
  }

  return { form: null, fields }
}

/**
 * A failed POST .../members, mapped to what the admin can act on. The
 * typed values are kept by the dialog whatever happens here.
 */
export function addMemberErrors(error: unknown): FormErrors<MemberField> {
  if (error instanceof ApiError && error.kind === 'http') {
    switch (error.status) {
      case 400:
        return error.body
          ? fieldValidationErrors(error.body.message, BACKEND_FIELD_LABELS)
          : { form: 'Please check the form and try again.', fields: {} }
      case 409:
        return { form: null, fields: { email: 'That email is already used by a QueueFlow account.' } }
      case 403:
        return { form: 'Only workspace admins can add members.', fields: {} }
      case 404:
        return {
          form: 'The member could not be added: this workspace is not available. Reload the page and try again.',
          fields: {},
        }
    }
  }
  return { form: genericErrorMessage(error), fields: {} }
}
