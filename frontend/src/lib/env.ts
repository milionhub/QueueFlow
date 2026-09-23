/**
 * The single place that reads Vite environment variables. Vite loads them
 * from the repository root .env (see envDir in vite.config.ts), and only
 * VITE_-prefixed variables ever reach the browser bundle.
 *
 * There is deliberately no default backend URL: a missing or invalid
 * VITE_API_BASE_URL is reported as null and surfaced by the API client as
 * a configuration error, instead of silently calling some guessed host.
 */
function readApiBaseUrl(): string | null {
  const raw = import.meta.env.VITE_API_BASE_URL?.trim()
  if (!raw) {
    return null
  }
  try {
    const url = new URL(raw)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null
    }
  } catch {
    return null
  }
  // Paths are joined as `${apiBaseUrl}/api/...`.
  return raw.replace(/\/+$/, '')
}

export const env = {
  apiBaseUrl: readApiBaseUrl(),
} as const
