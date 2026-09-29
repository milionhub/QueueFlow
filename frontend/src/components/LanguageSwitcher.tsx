import { Languages } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { LANGUAGES, setLanguage } from '../i18n'

/**
 * The interface language as two quiet text buttons ("English · Español"),
 * for the screens without an account menu (sign-in, registration). The
 * current language is pressed (aria-pressed) and in bold; each name is
 * written in its own language, so it can be found whichever is shown.
 */
export function LanguageSwitcher({ className = '' }: { className?: string }) {
  const { t, i18n } = useTranslation()
  return (
    <div role="group" aria-label={t('language.label')} className={`flex items-center gap-1 text-xs ${className}`}>
      <Languages aria-hidden="true" className="mr-0.5 size-3.5 text-ink-subtle" strokeWidth={2} />
      {LANGUAGES.map((language, index) => {
        const selected = i18n.language === language
        return (
          <span key={language} className="inline-flex items-center gap-1">
            {index > 0 && (
              <span aria-hidden="true" className="text-ink-subtle">
                ·
              </span>
            )}
            <button
              type="button"
              lang={language}
              aria-pressed={selected}
              onClick={() => setLanguage(language)}
              className={`rounded-sm px-1 py-1 underline-offset-4 transition-colors pointer-coarse:py-2 ${
                selected ? 'font-semibold text-ink' : 'font-medium text-ink-muted hover:text-ink hover:underline'
              }`}
            >
              {t(`language.${language}`)}
            </button>
          </span>
        )
      })}
    </div>
  )
}
