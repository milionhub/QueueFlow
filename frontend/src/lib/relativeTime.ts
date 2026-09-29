import { i18n } from '../i18n'

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const WEEK = 7 * DAY

/**
 * Browser-native formatting in the interface language: the compact units
 * ("5m", "2h", "3d" in English; "5min", "2h", "3d" in Spanish) and the
 * dates come from Intl, so only the words around them are translated. One
 * set of formatters per language, made the first time it is needed.
 */
interface Formatters {
  relative: Intl.RelativeTimeFormat
  monthDay: Intl.DateTimeFormat
  monthDayYear: Intl.DateTimeFormat
  full: Intl.DateTimeFormat
  units: Record<'minute' | 'hour' | 'day', Intl.NumberFormat>
}

const formattersByLocale = new Map<string, Formatters>()

function formatters(): Formatters {
  const locale = i18n.language
  let result = formattersByLocale.get(locale)
  if (!result) {
    const unit = (name: 'minute' | 'hour' | 'day') =>
      new Intl.NumberFormat(locale, { style: 'unit', unit: name, unitDisplay: 'narrow' })
    result = {
      relative: new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }),
      monthDay: new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' }),
      monthDayYear: new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', year: 'numeric' }),
      full: new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }),
      units: { minute: unit('minute'), hour: unit('hour'), day: unit('day') },
    }
    formattersByLocale.set(locale, result)
  }
  return result
}

/** A date format in the interface language, for code that formats its own dates (the activity's days). */
export function dateFormat(options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat(i18n.language, options)
}

export interface FormattedTime {
  /** For display: "now", "5m", "2h", "3d", then "Sep 3" or "Sep 3, 2025" (in the interface language). */
  short: string
  /** For assistive technology: "5 minutes ago", "yesterday", "on Sep 3". */
  spoken: string
  /** For a tooltip: the full local date and time. */
  full: string
}

/**
 * How long ago `iso` was, relative to `now`, in the interface language. A
 * timestamp slightly in the future (clock skew between browser and server)
 * counts as "now". Called while rendering: the component showing it uses
 * useTranslation, so it renders again when the language changes.
 */
export function formatRelativeTime(iso: string, now: number = Date.now()): FormattedTime {
  const { relative, monthDay, monthDayYear, full, units } = formatters()
  const date = new Date(iso)
  const elapsed = Math.max(0, now - date.getTime())
  const fullText = full.format(date)

  if (elapsed < MINUTE) {
    return { short: i18n.t('common:time.now'), spoken: i18n.t('common:time.justNow'), full: fullText }
  }
  if (elapsed < HOUR) {
    const minutes = Math.floor(elapsed / MINUTE)
    return { short: units.minute.format(minutes), spoken: relative.format(-minutes, 'minute'), full: fullText }
  }
  if (elapsed < DAY) {
    const hours = Math.floor(elapsed / HOUR)
    return { short: units.hour.format(hours), spoken: relative.format(-hours, 'hour'), full: fullText }
  }
  if (elapsed < WEEK) {
    const days = Math.floor(elapsed / DAY)
    return { short: units.day.format(days), spoken: relative.format(-days, 'day'), full: fullText }
  }
  const sameYear = date.getFullYear() === new Date(now).getFullYear()
  const short = (sameYear ? monthDay : monthDayYear).format(date)
  return { short, spoken: i18n.t('common:time.onDate', { date: short }), full: fullText }
}
