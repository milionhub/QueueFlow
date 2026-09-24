import { ApiError } from '../../api/errors'
import { fieldValidationErrors, genericErrorMessage, type FormErrors } from '../../lib/formErrors'

const PROJECT_FIELD_LABELS = {
  name: 'Name',
  key: 'Key',
  description: 'Description',
} as const

export type ProjectField = keyof typeof PROJECT_FIELD_LABELS

export interface ProjectErrors extends FormErrors<ProjectField> {
  /** The project no longer exists for this user (an edit answered 404): the list should be reloaded. */
  projectGone: boolean
}

/**
 * A failed create or edit, mapped to what the user can act on. `key` is
 * the normalized key that was submitted (create only): the only thing a
 * project conflict (409) can be about.
 */
export function projectErrors(error: unknown, mode: 'create' | 'edit', key?: string): ProjectErrors {
  const result = (errors: FormErrors<ProjectField>, projectGone = false): ProjectErrors => ({ ...errors, projectGone })
  if (error instanceof ApiError && error.kind === 'http') {
    switch (error.status) {
      case 409:
        return result({ form: null, fields: { key: `A project with key ${key ?? 'this key'} already exists.` } })
      case 400:
        if (error.body) {
          return result(fieldValidationErrors(error.body.message, PROJECT_FIELD_LABELS))
        }
        break
      case 403:
        return result({
          form: `Only workspace admins can ${mode === 'create' ? 'create' : 'edit'} projects.`,
          fields: {},
        })
      case 404:
        if (mode === 'edit') {
          return result({ form: 'This project is no longer available.', fields: {} }, true)
        }
        break
    }
  }
  return result({ form: genericErrorMessage(error), fields: {} })
}
