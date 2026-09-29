import { ApiError } from '../../api/errors'
import { i18n } from '../../i18n'
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

/** The member fields' names as the backend's messages use them, with their labels in the interface language. */
function backendFieldLabels(): Record<'name' | 'email' | 'password', string> {
  return {
    name: i18n.t('members:fields.name'),
    email: i18n.t('members:fields.email'),
    password: i18n.t('members:fields.password'),
  }
}

/**
 * Field messages for what can be told without the backend. Names and emails
 * are checked trimmed (the backend trims them); passwords exactly as typed.
 */
export function validateMember(values: MemberFormValues): FormErrors<MemberField> {
  const fields: Partial<Record<MemberField, string>> = {}

  const name = values.name.trim()
  if (name === '') {
    fields.name = i18n.t('members:validation.enterName')
  } else if (name.length > NAME_MAX_LENGTH) {
    fields.name = i18n.t('members:validation.nameTooLong', { max: NAME_MAX_LENGTH })
  }

  const email = values.email.trim()
  if (email === '') {
    fields.email = i18n.t('members:validation.enterEmail')
  } else if (email.length > EMAIL_MAX_LENGTH || !EMAIL_SYNTAX.test(email.toLowerCase())) {
    fields.email = i18n.t('members:validation.invalidEmail')
  }

  const { password, confirmPassword } = values
  if (password === '') {
    fields.password = i18n.t('members:validation.enterPassword')
  } else if ([...password].length < PASSWORD_MIN_CHARACTERS) {
    fields.password = i18n.t('members:validation.passwordTooShort', { min: PASSWORD_MIN_CHARACTERS })
  } else if (new TextEncoder().encode(password).length > PASSWORD_MAX_UTF8_BYTES) {
    fields.password = i18n.t('members:validation.passwordTooLong', { max: PASSWORD_MAX_UTF8_BYTES })
  }

  if (confirmPassword === '') {
    fields.confirmPassword = i18n.t('members:validation.confirmPassword')
  } else if (confirmPassword !== password) {
    fields.confirmPassword = i18n.t('members:validation.passwordsDiffer')
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
          ? fieldValidationErrors(error.body.message, backendFieldLabels())
          : { form: i18n.t('common:errors.checkForm'), fields: {} }
      case 409:
        return { form: null, fields: { email: i18n.t('members:add.emailTaken') } }
      case 403:
        return { form: i18n.t('members:add.forbidden'), fields: {} }
      case 404:
        return { form: i18n.t('members:add.workspaceGone'), fields: {} }
    }
  }
  return { form: genericErrorMessage(error), fields: {} }
}

/** The member no longer exists in this workspace (removed meanwhile, or never here): 404. */
export function isMemberGone(error: unknown): boolean {
  return error instanceof ApiError && error.kind === 'http' && error.status === 404
}

/** A failed rename, mapped to what the admin can act on. The typed name is kept by the dialog. */
export function editMemberErrors(error: unknown): FormErrors<'name'> {
  if (error instanceof ApiError && error.kind === 'http') {
    switch (error.status) {
      case 400:
        return error.body
          ? fieldValidationErrors(error.body.message, { name: i18n.t('members:fields.name') })
          : { form: i18n.t('members:edit.checkName'), fields: {} }
      case 403:
        return { form: i18n.t('members:edit.forbidden'), fields: {} }
      case 404:
        return { form: i18n.t('members:gone'), fields: {} }
    }
  }
  return { form: genericErrorMessage(error), fields: {} }
}

/**
 * Why a removal failed, as one sentence for the confirmation dialog. The
 * backend's two rules for who can be removed come as English sentences
 * (UserService.removeMember); they are recognized and translated here, and
 * any other 400 gets a general sentence.
 */
export function removeMemberError(error: unknown): string {
  if (error instanceof ApiError && error.kind === 'http') {
    switch (error.status) {
      case 400:
        if (error.body?.message === 'You cannot remove yourself from the workspace') {
          return i18n.t('members:remove.cannotRemoveSelf')
        }
        if (error.body?.message === 'Only members can be removed, not admins') {
          return i18n.t('members:remove.cannotRemoveAdmin')
        }
        return i18n.t('members:remove.cannotRemove')
      case 403:
        return i18n.t('members:remove.forbidden')
      case 404:
        return i18n.t('members:gone')
    }
  }
  return genericErrorMessage(error)
}
