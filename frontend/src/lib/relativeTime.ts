const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const WEEK = 7 * DAY

// Browser-native formatting only. English for now, like the rest of the UI.
const LOCALE = 'en'
const relative = new Intl.RelativeTimeFormat(LOCALE, { numeric: 'auto' })
const monthDay = new Intl.DateTimeFormat(LOCALE, { month: 'short', day: 'numeric' })
const monthDayYear = new Intl.DateTimeFormat(LOCALE, { month: 'short', day: 'numeric', year: 'numeric' })
const full = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })

function unit(value: number, name: 'minute' | 'hour' | 'day'): string {
  return new Intl.NumberFormat(LOCALE, { style: 'unit', unit: name, unitDisplay: 'narrow' }).format(value)
}

export interface FormattedTime {
  /** For display: "now", "5m", "2h", "3d", then "Sep 3" or "Sep 3, 2025". */
  short: string
  /** For assistive technology: "5 minutes ago", "yesterday", "on Sep 3". */
  spoken: string
  /** For a tooltip: the full local date and time. */
  full: string
}

/**
 * How long ago `iso` was, relative to `now`. A timestamp slightly in the
 * future (clock skew between browser and server) counts as "now".
 */
export function formatRelativeTime(iso: string, now: number = Date.now()): FormattedTime {
  const date = new Date(iso)
  const elapsed = Math.max(0, now - date.getTime())
  const fullText = full.format(date)

  if (elapsed < MINUTE) {
    return { short: 'now', spoken: 'just now', full: fullText }
  }
  if (elapsed < HOUR) {
    const minutes = Math.floor(elapsed / MINUTE)
    return { short: unit(minutes, 'minute'), spoken: relative.format(-minutes, 'minute'), full: fullText }
  }
  if (elapsed < DAY) {
    const hours = Math.floor(elapsed / HOUR)
    return { short: unit(hours, 'hour'), spoken: relative.format(-hours, 'hour'), full: fullText }
  }
  if (elapsed < WEEK) {
    const days = Math.floor(elapsed / DAY)
    return { short: unit(days, 'day'), spoken: relative.format(-days, 'day'), full: fullText }
  }
  const sameYear = date.getFullYear() === new Date(now).getFullYear()
  const short = (sameYear ? monthDay : monthDayYear).format(date)
  return { short, spoken: `on ${short}`, full: fullText }
}
