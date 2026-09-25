import type { UserRole } from './types'

/** How the two roles are named in the UI. */
export const ROLE_LABELS: Record<UserRole, string> = { ADMIN: 'Admin', MEMBER: 'Member' }

/** Up to two initials from a user's real name, for an avatar. */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  const letters = parts.length > 1 ? [parts[0], parts[parts.length - 1]] : parts
  return letters.map((part) => [...part][0]?.toUpperCase() ?? '').join('') || '?'
}
