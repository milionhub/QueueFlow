import { X, type LucideIcon } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'

import { IconButton } from './IconButton'

export type DialogTone = 'default' | 'danger'

interface DialogProps {
  title: string
  description?: ReactNode
  /** A small symbol of what the dialog is about, shown beside the title. Decorative. */
  icon?: LucideIcon
  /** `danger` for confirming something that cannot be undone: the icon and header take the danger hue. */
  tone?: DialogTone
  /** Called for Escape, the close button, or a close the browser forces. */
  onClose: () => void
  /** While false (e.g. a request is under way), Escape and the close button do nothing. */
  dismissible?: boolean
  /**
   * The control that opened the dialog, which gets focus back on close.
   * Defaults to whatever had focus on open - not enough on its own, since
   * Safari does not focus a button when it is clicked.
   */
  returnFocus?: HTMLElement | null
  /** Where focus goes on close when the control that opened the dialog is gone. */
  fallbackFocus?: () => HTMLElement | null
  /** `lg` for forms with more fields. Below `sm` both are a bottom sheet across the whole width. */
  size?: 'md' | 'lg'
  children: ReactNode
}

const SIZE_CLASSES = { md: 'max-w-lg', lg: 'max-w-xl' } as const

/** The header's faint wash and the icon tile, per tone: a hint of the hue, never a filled block. */
const TONE_CLASSES: Record<DialogTone, { header: string; icon: string }> = {
  default: {
    header: 'from-accent-subtle/70',
    icon: 'bg-accent-subtle text-accent ring-accent/15',
  },
  danger: {
    header: 'from-danger-subtle/70',
    icon: 'bg-danger-subtle text-danger ring-danger/15',
  },
}

/**
 * A modal on the native <dialog>: showModal() puts it in the top layer and
 * makes the rest of the page inert. It opens when mounted and closes when
 * unmounted, returning focus to the element that opened it. The first
 * element marked `data-autofocus` receives focus when it opens.
 *
 * Every dialog has the same three parts: a header (optional icon tile,
 * title, description, close button) over a faint wash of its tone, the
 * body (DialogBody), and the action row (DialogFooter) on a canvas-toned
 * band. Separators between them keep long forms readable.
 *
 * It enters with a short fade and rise (global.css); below `sm` it is a
 * bottom sheet, easier to reach with a thumb and above the on-screen
 * keyboard. Either way it is the same native dialog, so focus containment,
 * Escape and focus return do not change.
 */
export function Dialog({
  title,
  description,
  icon: Icon,
  tone = 'default',
  onClose,
  dismissible = true,
  returnFocus,
  fallbackFocus,
  size = 'md',
  children,
}: DialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  // Read from the native event handlers without re-registering them.
  const latest = useRef({ onClose, dismissible, returnFocus, fallbackFocus })
  useEffect(() => {
    latest.current = { onClose, dismissible, returnFocus, fallbackFocus }
  })

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    const focused = document.activeElement
    const opener =
      latest.current.returnFocus ?? (focused instanceof HTMLElement && focused !== document.body ? focused : null)
    let unmounting = false

    function onCancel(event: Event) {
      event.preventDefault()
      if (latest.current.dismissible) {
        latest.current.onClose()
      }
    }
    // The browser may still close the dialog itself (Chrome closes it on a
    // repeated Escape even when cancel is prevented). A close event that
    // arrives while the dialog is open is stale: it was queued by an earlier
    // close() - e.g. React StrictMode's unmount/remount - and is ignored.
    function onNativeClose() {
      if (unmounting || dialog?.open) {
        return
      }
      if (latest.current.dismissible) {
        latest.current.onClose()
      } else if (dialog && dialog.isConnected) {
        dialog.showModal()
      }
    }

    dialog.addEventListener('cancel', onCancel)
    dialog.addEventListener('close', onNativeClose)
    dialog.showModal()
    dialog.querySelector<HTMLElement>('[data-autofocus]')?.focus()

    return () => {
      unmounting = true
      dialog.removeEventListener('cancel', onCancel)
      dialog.removeEventListener('close', onNativeClose)
      if (dialog.open) {
        dialog.close()
      }
      const target = opener?.isConnected ? opener : latest.current.fallbackFocus?.()
      target?.focus()
    }
  }, [])

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      className={`queueflow-dialog m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] ${SIZE_CLASSES[size]} overflow-y-auto overscroll-contain rounded-xl border border-line bg-surface p-0 text-ink shadow-xl backdrop:bg-ink/35 max-sm:mx-0 max-sm:mt-auto max-sm:mb-0 max-sm:max-h-[calc(100dvh-1.5rem)] max-sm:w-full max-sm:max-w-none max-sm:rounded-b-none max-sm:rounded-t-2xl max-sm:border-x-0 max-sm:border-b-0`}
    >
      <div
        className={`queueflow-dialog-header flex items-start gap-3.5 border-b border-line bg-linear-to-b ${TONE_CLASSES[tone].header} to-surface to-80% px-5 pt-5 pb-4`}
      >
        {Icon && (
          <span
            aria-hidden="true"
            className={`flex size-10 shrink-0 items-center justify-center rounded-lg ring-1 ring-inset ${TONE_CLASSES[tone].icon}`}
          >
            <Icon className="size-5" strokeWidth={1.75} />
          </span>
        )}
        <div className={`min-w-0 flex-1 ${Icon ? 'pt-px' : ''}`}>
          <h2 id={titleId} className="text-lg leading-7 font-semibold tracking-tight text-ink">
            {title}
          </h2>
          {description && (
            <p id={descriptionId} className="mt-0.5 text-sm leading-6 text-ink-muted">
              {description}
            </p>
          )}
        </div>
        <IconButton
          icon={X}
          label="Close"
          onClick={() => dismissible && onClose()}
          disabled={!dismissible}
          className="-mt-1 -mr-1.5"
        />
      </div>
      {children}
    </dialog>
  )
}

/** The dialog's content between header and footer, with the shared padding and rhythm. */
export function DialogBody({ children }: { children: ReactNode }) {
  return <div className="flex flex-col gap-5 px-5 py-5">{children}</div>
}

/**
 * Related fields inside a dialog (a ticket's properties, a member's sign-in
 * details) on a quiet inset panel with a small heading, which also names the
 * group for assistive technology.
 */
export function DialogFieldGroup({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId()
  return (
    <div
      role="group"
      aria-labelledby={headingId}
      className="flex flex-col gap-4 rounded-lg bg-canvas p-4 ring-1 ring-line ring-inset"
    >
      <p id={headingId} className="text-xs leading-4 font-semibold tracking-wide text-ink-muted uppercase">
        {title}
      </p>
      {children}
    </div>
  )
}

/**
 * The dialog's action row: secondary actions first, the main one last. It
 * sits on a canvas-toned band and stays at the bottom while a long form
 * scrolls; on phones the buttons are full width and clear of the home
 * indicator. Right under the header (a confirmation with no body) it drops
 * its own separator, so there is never a double line.
 */
export function DialogFooter({ children }: { children: ReactNode }) {
  return (
    <div className="sticky bottom-0 flex flex-col-reverse gap-2 border-t border-line bg-canvas px-5 py-4 max-sm:pb-[max(1rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-end [&>button]:max-sm:h-11 [.queueflow-dialog-header+&]:border-t-0">
      {children}
    </div>
  )
}
