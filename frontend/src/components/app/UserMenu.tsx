import { Check, ChevronsUpDown, Languages, LogOut } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { useAuth } from '../../features/auth/useAuth'
import { roleLabel } from '../../features/auth/userDisplay'
import { LANGUAGES, setLanguage, type Language } from '../../i18n'
import { Avatar } from '../ui/Avatar'

const ITEM =
  'flex h-9 w-full items-center gap-2 rounded-md px-2 text-sm text-ink transition-colors hover:bg-canvas-strong focus-visible:bg-canvas-strong pointer-coarse:h-11'

/**
 * The signed-in user at the bottom of the sidebar. Opens a small menu with
 * the email, the interface language (English or Español, the current one
 * checked) and "Sign out" (client-side only, see AuthProvider.logout).
 * Arrow keys move between the items; Escape closes the menu.
 */
export function UserMenu() {
  const { t, i18n } = useTranslation(['shell', 'common'])
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const languageHeadingId = useId()

  useEffect(() => {
    if (!open) {
      return
    }
    menuRef.current?.querySelector<HTMLElement>('[role^="menuitem"]')?.focus()
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
      } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        const items = [...(menuRef.current?.querySelectorAll<HTMLElement>('[role^="menuitem"]') ?? [])]
        const index = items.indexOf(document.activeElement as HTMLElement)
        if (index !== -1) {
          event.preventDefault()
          const step = event.key === 'ArrowDown' ? 1 : -1
          items[(index + step + items.length) % items.length]?.focus()
        }
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

  function chooseLanguage(language: Language) {
    setLanguage(language)
    setOpen(false)
    triggerRef.current?.focus()
  }

  return (
    <div ref={containerRef} className="relative">
      {open && (
        <div
          ref={menuRef}
          id={menuId}
          role="menu"
          aria-label={t('account.menu')}
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
          <div role="group" aria-labelledby={languageHeadingId}>
            <p
              id={languageHeadingId}
              className="flex items-center gap-1.5 px-2 pt-1 pb-1 text-[11px] leading-4 font-medium tracking-wide text-ink-muted uppercase"
            >
              <Languages aria-hidden="true" className="size-3.5" strokeWidth={2} />
              {t('account.language')}
            </p>
            {LANGUAGES.map((language) => {
              const selected = i18n.language === language
              return (
                <button
                  key={language}
                  type="button"
                  role="menuitemradio"
                  aria-checked={selected}
                  lang={language}
                  onClick={() => chooseLanguage(language)}
                  className={`${ITEM} ${selected ? 'font-medium' : ''}`}
                >
                  <Check
                    aria-hidden="true"
                    className={`size-4 shrink-0 text-accent ${selected ? '' : 'invisible'}`}
                    strokeWidth={2.25}
                  />
                  {t(`common:language.${language}`)}
                </button>
              )
            })}
          </div>
          <div className="my-1 border-t border-line" />
          <button type="button" role="menuitem" onClick={signOut} className={ITEM}>
            <LogOut aria-hidden="true" className="size-4 text-ink-muted" strokeWidth={2} />
            {t('account.signOut')}
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
          <span className="block truncate text-xs text-ink-muted">{roleLabel(user.role)}</span>
        </span>
        <ChevronsUpDown aria-hidden="true" className="size-4 shrink-0 text-ink-subtle" strokeWidth={2} />
        <span className="sr-only">{t('account.triggerSuffix')}</span>
      </button>
    </div>
  )
}
