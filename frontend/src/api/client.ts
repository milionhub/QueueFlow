import { env } from '../lib/env'
import { ApiError, isApiErrorBody } from './errors'

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface ApiRequestOptions {
  method?: HttpMethod
  /** Serialized as JSON. */
  body?: unknown
  /** Sent as `Authorization: Bearer <token>` when present. */
  accessToken?: string
  signal?: AbortSignal
}

/**
 * The one way the frontend calls the backend: native fetch, JSON in and
 * out, and every failure turned into an ApiError. Resolves with the parsed
 * JSON body, or undefined for a response without one (e.g. 204).
 *
 * @param path absolute backend path, e.g. "/api/projects"
 */
export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { method = 'GET', body, accessToken, signal } = options

  if (env.apiBaseUrl === null) {
    throw new ApiError('configuration', 'The frontend is not configured: VITE_API_BASE_URL is missing or invalid.')
  }

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  let response: Response
  try {
    response = await fetch(`${env.apiBaseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (cause) {
    // Aborts belong to the caller (e.g. a component unmounting): rethrow as is.
    if (signal?.aborted) {
      throw cause
    }
    throw new ApiError('network', 'The server could not be reached.')
  }

  const data = await readJson(response)

  if (!response.ok) {
    if (isApiErrorBody(data)) {
      throw new ApiError('http', data.message, response.status, data)
    }
    throw new ApiError('http', `The server responded with status ${response.status}.`, response.status)
  }

  return data as T
}

/** The parsed JSON body, or undefined when there is none or it is not JSON. */
async function readJson(response: Response): Promise<unknown> {
  const contentType = response.headers.get('Content-Type') ?? ''
  if (response.status === 204 || !contentType.includes('json')) {
    return undefined
  }
  try {
    return await response.json()
  } catch {
    return undefined
  }
}
