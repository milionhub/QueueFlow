import { ChevronsUpDown, LogOut } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useNavigate } from 'react-router'

import { useAuth } from '../../features/auth/useAuth'

const ROLE_LABELS = { ADMIN: 'Admin', MEMBER: 'Member' } as const

/** Up to two initials from the user's real name, for the avatar. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  const letters = parts.length > 1 ? [parts[0], parts[parts.length - 1]] : parts
  return letters.map((part) => [...part][0]?.toUpperCase() ?? '').join('') || '?'
}

/**
 * The signed-in user at the bottom of the sidebar. Opens a small menu with
 * the email and "Sign out" (client-side only, see AuthProvider.logout).
 */
export function UserMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const firstItemRef = useRef<HTMLButtonElement>(null)
  const menuId = useId()

  useEffect(() => {
    if (!open) {
      return
    }
    firstItemRef.current?.focus()
    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.stopPropagation()
        setOpen(false)
        triggerRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown, true)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown, true)
    }
  }, [open])

  if (!user) {
    return null
  }

  function signOut() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div ref={containerRef} className="relative">
      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label="Account"
          className="absolute inset-x-0 bottom-full mb-1.5 rounded-md border border-line bg-surface p-1 shadow-md"
        >
          <div className="px-2 py-1.5">
            <p className="truncate text-sm font-medium text-ink">{user.name}</p>
            <p className="truncate text-xs text-ink-muted" title={user.email}>
              {user.email}
            </p>
          </div>
          <div className="my-1 border-t border-line" />
          <button
            ref={firstItemRef}
            type="button"
            role="menuitem"
            onClick={signOut}
            className="flex h-8 w-full items-center gap-2 rounded px-2 text-sm text-ink hover:bg-canvas focus-visible:bg-canvas"
          >
            <LogOut aria-hidden="true" className="size-4 text-ink-muted" strokeWidth={2} />
            Sign out
          </button>
        </div>
      )}

      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        className="flex w-full items-center gap-2.5 rounded-md p-2 text-left hover:bg-line/60"
      >
        <span
          aria-hidden="true"
          className="inline-flex size-8 shrink-0 items-center justify-center rounded-md bg-accent-subtle text-xs font-semibold text-accent"
        >
          {initials(user.name)}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium text-ink">{user.name}</span>
          <span className="block truncate text-xs text-ink-muted">{ROLE_LABELS[user.role]}</span>
        </span>
        <ChevronsUpDown aria-hidden="true" className="size-4 shrink-0 text-ink-subtle" strokeWidth={2} />
        <span className="sr-only">, account menu</span>
      </button>
    </div>
  )
}
