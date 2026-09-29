import { ApiError } from '../../api/errors'
import { i18n } from '../../i18n'
import { fieldValidationErrors, genericErrorMessage, type FormErrors } from '../../lib/formErrors'

export type ProjectField = 'name' | 'key' | 'description'

/** The project fields' names as the backend's messages use them, with their labels in the interface language. */
function projectFieldLabels(): Record<ProjectField, string> {
  return {
    name: i18n.t('projects:form.name'),
    key: i18n.t('projects:form.key'),
    description: i18n.t('projects:form.description'),
  }
}

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
        return result({
          form: null,
          fields: {
            key: key ? i18n.t('projects:errors.keyTaken', { key }) : i18n.t('projects:errors.keyTakenUnknown'),
          },
        })
      case 400:
        if (error.body) {
          return result(fieldValidationErrors(error.body.message, projectFieldLabels()))
        }
        break
      case 403:
        return result({
          form: mode === 'create' ? i18n.t('projects:errors.forbiddenCreate') : i18n.t('projects:errors.forbiddenEdit'),
          fields: {},
        })
      case 404:
        if (mode === 'edit') {
          return result({ form: i18n.t('projects:errors.gone'), fields: {} }, true)
        }
        break
    }
  }
  return result({ form: genericErrorMessage(error), fields: {} })
}
