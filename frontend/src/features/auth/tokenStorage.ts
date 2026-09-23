/**
 * The only place the access token is stored.
 *
 * sessionStorage: the token survives a reload of the same tab and is gone
 * when the tab closes; nothing lingers on the device. It is still readable
 * by any script on the page (like any storage JavaScript can use), so it is
 * no protection against XSS - that protection is not injecting scripts in
 * the first place. The backend has no cookie authentication or refresh
 * tokens, so a Bearer token held by the client is the V1 design.
 *
 * If sessionStorage is unavailable (e.g. disabled by the browser), the token
 * is kept in memory for the life of the page only.
 */
const STORAGE_KEY = 'queueflow.accessToken'

let memoryToken: string | null = null

function storage(): Storage | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

export const tokenStorage = {
  get(): string | null {
    try {
      return storage()?.getItem(STORAGE_KEY) ?? memoryToken
    } catch {
      return memoryToken
    }
  },

  set(token: string): void {
    memoryToken = token
    try {
      storage()?.setItem(STORAGE_KEY, token)
    } catch {
      // Storage full or blocked: the in-memory copy still serves this page.
    }
  },

  clear(): void {
    memoryToken = null
    try {
      storage()?.removeItem(STORAGE_KEY)
    } catch {
      // Nothing stored.
    }
  },
}
