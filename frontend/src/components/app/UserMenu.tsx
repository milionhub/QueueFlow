import { ChevronsUpDown, LogOut } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useNavigate } from 'react-router'

import { useAuth } from '../../features/auth/useAuth'
import { ROLE_LABELS } from '../../features/auth/userDisplay'
import { Avatar } from '../ui/Avatar'

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
          className="absolute inset-x-0 bottom-full mb-1.5 origin-bottom animate-pop rounded-lg border border-line bg-surface p-1 shadow-lg"
        >
          <div className="flex items-center gap-2.5 px-2 py-2">
            <Avatar name={user.name} seed={user.id} size="md" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-ink">{user.name}</p>
              <p className="truncate text-xs text-ink-muted" title={user.email}>
                {user.email}
              </p>
            </div>
          </div>
          <div className="my-1 border-t border-line" />
          <button
            ref={firstItemRef}
            type="button"
            role="menuitem"
            onClick={signOut}
            className="flex h-9 w-full items-center gap-2 rounded-md px-2 text-sm text-ink transition-colors hover:bg-canvas-strong focus-visible:bg-canvas-strong pointer-coarse:h-11"
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
        className={`press flex w-full items-center gap-2.5 rounded-md p-2 text-left transition-colors hover:bg-canvas-strong ${
          open ? 'bg-canvas-strong' : ''
        }`}
      >
        <Avatar name={user.name} seed={user.id} size="md" />
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
