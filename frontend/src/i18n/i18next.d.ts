import 'i18next'

import type { en } from './locales/en'

/**
 * English is the reference: every key used in code must exist in it (a
 * typo is a type error), and the Spanish messages are checked against the
 * same shape (see locales/es).
 */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common'
    resources: typeof en
    returnNull: false
  }
}
