/**
 * The single place that reads Vite environment variables. Vite loads them
 * from the repository root .env (see envDir in vite.config.ts), and only
 * VITE_-prefixed variables ever reach the browser bundle. They are fixed
 * when the bundle is built.
 *
 * VITE_API_BASE_URL is either:
 * - an absolute http(s) URL of the backend, e.g. http://localhost:8080 in
 *   local development (a trailing slash is ignored); or
 * - exactly "/", meaning the backend is reached through the page's own
 *   origin - the Docker image, where nginx serves the app and proxies /api
 *   to the backend. It becomes that origin (e.g. http://localhost:8088)
 *   when the app loads, so the rest of the app always sees an origin.
 *
 * There is deliberately no default backend URL: a missing or invalid value
 * (anything else, e.g. "api" or "/api") is reported as null and surfaced by
 * the API client as a configuration error, instead of silently calling some
 * guessed host.
 */
function readApiBaseUrl(): string | null {
  const raw = import.meta.env.VITE_API_BASE_URL?.trim()
  if (!raw) {
    return null
  }
  if (raw === '/') {
    return window.location.origin
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
