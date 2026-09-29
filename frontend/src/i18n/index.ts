import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'

import { en } from './locales/en'
import { es } from './locales/es'

/**
 * The interface languages. English is the default for everyone: the
 * browser's language is deliberately not consulted, so a first visit is
 * always the same. A choice made in the interface is remembered in this
 * browser (a UI preference only - nothing about the session or the user).
 */
export const LANGUAGES = ['en', 'es'] as const
export type Language = (typeof LANGUAGES)[number]
export const DEFAULT_LANGUAGE: Language = 'en'

const STORAGE_KEY = 'queueflow.language'

function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (LANGUAGES as readonly string[]).includes(value)
}

function storedLanguage(): Language {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY)
    return isLanguage(value) ? value : DEFAULT_LANGUAGE
  } catch {
    // Storage unavailable (private mode, blocked): the default, every time.
    return DEFAULT_LANGUAGE
  }
}

/**
 * Spanish has a third plural category, `many`, for round millions
 * ("1.000.000 tickets"). Its words are those of `other`, so every `_other`
 * key gets a `_many` twin here instead of in the message files - without
 * it i18next would show the bare key for such counts.
 */
function withManyForms<T extends object>(messages: T): T {
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(messages)) {
    if (value !== null && typeof value === 'object') {
      result[key] = withManyForms(value)
    } else {
      result[key] = value
      const base = key.endsWith('_other') ? key.slice(0, -'_other'.length) : null
      if (base !== null && !(`${base}_many` in messages)) {
        result[`${base}_many`] = value
      }
    }
  }
  return result as T
}

export const NAMESPACES = Object.keys(en) as (keyof typeof en)[]

void i18next.use(initReactI18next).init({
  resources: { en, es: withManyForms(es) },
  lng: storedLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: LANGUAGES,
  ns: NAMESPACES,
  defaultNS: 'common',
  // React already escapes what it renders.
  interpolation: { escapeValue: false },
  returnNull: false,
  // The messages are bundled: nothing to wait for, so no Suspense.
  initAsync: false,
  react: { useSuspense: false },
})

function applyDocumentLanguage(language: string) {
  document.documentElement.lang = language
}
applyDocumentLanguage(i18next.language)
i18next.on('languageChanged', applyDocumentLanguage)

/** The interface language now in use. */
export function currentLanguage(): Language {
  return isLanguage(i18next.language) ? i18next.language : DEFAULT_LANGUAGE
}

/** Switches the whole interface at once, without a reload, and remembers the choice in this browser. */
export function setLanguage(language: Language) {
  try {
    window.localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // Not remembered, but still applied for this visit.
  }
  void i18next.changeLanguage(language)
}

export const i18n = i18next
