import { MoreHorizontal, Pencil, UserMinus } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'

import { IconButton } from '../../../components/ui/IconButton'

interface MemberActionsMenuProps {
  /** Names the trigger and the menu for assistive technology, e.g. "Actions for Laura". */
  memberName: string
  /** `opener` is the ⋯ button: what focus returns to when the dialog closes. */
  onEdit: (opener: HTMLElement) => void
  onRemove: (opener: HTMLElement) => void
}

/**
 * An ADMIN's actions on one member row, behind a quiet "⋯" button so they
 * never compete with the member's name. The same small menu as a comment's
 * on touch screens: arrow keys move between the items, Escape closes it and
 * returns to the button, a tap or click elsewhere closes it.
 */
export function MemberActionsMenu({ memberName, onEdit, onRemove }: MemberActionsMenuProps) {
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const label = `Actions for ${memberName}`

  useEffect(() => {
    if (!open) {
      return
    }
    containerRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      setOpen(false)
      triggerRef.current?.focus()
    } else if (event.key === 'Tab') {
      // Leaving the menu closes it; focus moves on as usual.
      setOpen(false)
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const items = [...(containerRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]
      const index = items.indexOf(document.activeElement as HTMLElement)
      const step = event.key === 'ArrowDown' ? 1 : -1
      items[(index + step + items.length) % items.length]?.focus()
    }
  }

  function choose(action: (opener: HTMLElement) => void) {
    setOpen(false)
    if (triggerRef.current) {
      action(triggerRef.current)
    }
  }

  return (
    <div ref={containerRef} className="relative shrink-0">
      <IconButton
        ref={triggerRef}
        icon={MoreHorizontal}
        label={label}
        title="Member actions"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => setOpen((current) => !current)}
      />
      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label={label}
          onKeyDown={handleMenuKeyDown}
          className="absolute top-full right-0 z-20 mt-1 w-48 origin-top-right animate-pop rounded-lg border border-line bg-surface p-1 shadow-lg"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => choose(onEdit)}
            className="flex h-10 w-full items-center gap-2 rounded-md px-2.5 text-sm text-ink hover:bg-canvas-strong focus-visible:bg-canvas-strong pointer-coarse:h-11"
          >
            <Pencil aria-hidden="true" className="size-4 text-ink-muted" strokeWidth={2} />
            Edit member
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => choose(onRemove)}
            className="flex h-10 w-full items-center gap-2 rounded-md px-2.5 text-sm text-danger hover:bg-danger/5 focus-visible:bg-danger/5 pointer-coarse:h-11"
          >
            <UserMinus aria-hidden="true" className="size-4" strokeWidth={2} />
            Remove member
          </button>
        </div>
      )}
    </div>
  )
}
