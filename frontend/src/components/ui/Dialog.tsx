import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'

interface DialogProps {
  title: string
  description?: ReactNode
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
  /** `lg` for forms with more fields; on phones both fill the width minus the margins. */
  size?: 'md' | 'lg'
  children: ReactNode
}

const SIZE_CLASSES = { md: 'max-w-lg', lg: 'max-w-xl' } as const

/**
 * A modal on the native <dialog>: showModal() puts it in the top layer and
 * makes the rest of the page inert. It opens when mounted and closes when
 * unmounted, returning focus to the element that opened it. The first
 * element marked `data-autofocus` receives focus when it opens.
 */
export function Dialog({
  title,
  description,
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
      className={`m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] ${SIZE_CLASSES[size]} overflow-y-auto rounded-lg border border-line bg-surface p-0 text-ink shadow-xl backdrop:bg-ink/30`}
    >
      <div className="flex items-start justify-between gap-4 px-5 pt-5">
        <div className="min-w-0">
          <h2 id={titleId} className="text-base font-semibold text-ink">
            {title}
          </h2>
          {description && (
            <p id={descriptionId} className="mt-1 text-sm leading-6 text-ink-muted">
              {description}
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={() => dismissible && onClose()}
          disabled={!dismissible}
          aria-label="Close"
          className="-mt-1 -mr-1.5 inline-flex size-8 shrink-0 items-center justify-center rounded-md text-ink-muted hover:bg-canvas hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
        >
          <X aria-hidden="true" className="size-4" strokeWidth={2} />
        </button>
      </div>
      {children}
    </dialog>
  )
}

/** The dialog's action row: secondary actions first, the main one last. */
export function DialogFooter({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col-reverse gap-2 border-t border-line px-5 py-4 sm:flex-row sm:justify-end">
      {children}
    </div>
  )
}
