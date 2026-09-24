/**
 * The backend's standard error body (ApiErrorResponse), returned for every
 * 4xx/5xx the API decides: validation, authentication, authorization, not
 * found, conflicts.
 */
export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}

/**
 * Why a request failed:
 * - `http`: the backend answered with a non-2xx status (`body` is set when
 *   it used the standard error contract);
 * - `network`: no usable response (backend down, CORS refusal, offline);
 * - `configuration`: the frontend has no valid VITE_API_BASE_URL.
 */
export type ApiErrorKind = 'http' | 'network' | 'configuration'

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  /** HTTP status, or 0 when there was no response. */
  readonly status: number
  readonly body: ApiErrorBody | null

  constructor(kind: ApiErrorKind, message: string, status = 0, body: ApiErrorBody | null = null) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
    this.body = body
  }
}

export function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.timestamp === 'string' &&
    typeof candidate.status === 'number' &&
    typeof candidate.error === 'string' &&
    typeof candidate.message === 'string' &&
    typeof candidate.path === 'string'
  )
}

/**
 * Why a page's data could not be loaded, as far as the reader cares: no
 * response at all, or a response that failed. (A 401 never gets this far:
 * authorizedRequest ends the session.)
 */
export type LoadFailure = 'network' | 'server'

export function loadFailureOf(error: unknown): LoadFailure {
  return error instanceof ApiError && error.kind === 'network' ? 'network' : 'server'
}
