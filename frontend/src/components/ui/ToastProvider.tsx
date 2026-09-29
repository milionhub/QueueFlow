import { CircleAlert, CircleCheck, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { ToastContext, type ToastApi, type ToastInput } from './toastContext'

interface ToastItem extends ToastInput {
  id: number
  leaving: boolean
}

const DURATION = { success: 4000, error: 8000 } as const
const MAX_VISIBLE = 3

/**
 * The application's toasts: bottom-right on larger screens, bottom-centre
 * on phones. Two live regions are always in the page - polite for results,
 * assertive for errors - so each toast is announced once, when it is added.
 * A toast leaves after 4s (errors 8s), paused while hovered or focused, and
 * can be dismissed.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(0)

  const remove = useCallback((id: number) => setToasts((current) => current.filter((toast) => toast.id !== id)), [])
  const dismiss = useCallback(
    (id: number) =>
      setToasts((current) => current.map((toast) => (toast.id === id ? { ...toast, leaving: true } : toast))),
    [],
  )

  const api = useMemo<ToastApi>(
    () => ({
      show(input) {
        const id = ++nextId.current
        setToasts((current) => [...current, { ...input, id, leaving: false }].slice(-MAX_VISIBLE))
      },
    }),
    [],
  )

  const polite = toasts.filter((toast) => toast.tone !== 'error')
  const assertive = toasts.filter((toast) => toast.tone === 'error')

  return (
    <ToastContext value={api}>
      {children}
      <section
        aria-label={t('toast.region')}
        className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 px-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:inset-x-auto sm:right-0 sm:items-end sm:px-5 sm:pb-5"
      >
        <div role="alert" className="flex w-full flex-col gap-2 sm:w-96">
          {assertive.map((toast) => (
            <Toast key={toast.id} toast={toast} onDismiss={dismiss} onGone={remove} />
          ))}
        </div>
        <div role="status" className="flex w-full flex-col gap-2 sm:w-96">
          {polite.map((toast) => (
            <Toast key={toast.id} toast={toast} onDismiss={dismiss} onGone={remove} />
          ))}
        </div>
      </section>
    </ToastContext>
  )
}

interface ToastProps {
  toast: ToastItem
  onDismiss: (id: number) => void
  onGone: (id: number) => void
}

function Toast({ toast, onDismiss, onGone }: ToastProps) {
  const { t } = useTranslation()
  const tone = toast.tone ?? 'success'
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const remaining = useRef<number>(DURATION[tone])
  const paused = hovered || focused

  // Counts down only while nobody is looking at it (hover) or in it (focus).
  useEffect(() => {
    if (paused || toast.leaving) {
      return
    }
    const started = Date.now()
    const timer = window.setTimeout(() => onDismiss(toast.id), remaining.current)
    return () => {
      window.clearTimeout(timer)
      remaining.current -= Date.now() - started
    }
  }, [paused, toast.leaving, toast.id, onDismiss])

  // Removed once its exit has played (immediately with reduced motion; the
  // timer covers a browser that never reports the animation's end).
  useEffect(() => {
    if (!toast.leaving) {
      return
    }
    const timer = window.setTimeout(() => onGone(toast.id), 200)
    return () => window.clearTimeout(timer)
  }, [toast.leaving, toast.id, onGone])

  const Icon = tone === 'error' ? CircleAlert : CircleCheck
  const action = toast.action
  return (
    <div
      onPointerEnter={() => setHovered(true)}
      onPointerLeave={() => setHovered(false)}
      onFocus={() => setFocused(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          setFocused(false)
        }
      }}
      onAnimationEnd={() => toast.leaving && onGone(toast.id)}
      className={`pointer-events-auto flex w-full items-start gap-3 rounded-lg border border-line bg-surface py-3 pr-2 pl-3.5 text-sm shadow-lg ${
        toast.leaving ? 'animate-toast-out' : 'animate-toast-in'
      }`}
    >
      <Icon
        aria-hidden="true"
        className={`mt-0.5 size-4 shrink-0 ${tone === 'error' ? 'text-danger' : 'text-success'}`}
        strokeWidth={2}
      />
      <p className="min-w-0 flex-1 leading-5 break-words text-ink">{toast.message}</p>
      {action &&
        ('to' in action ? (
          <Link
            to={action.to}
            onClick={() => onDismiss(toast.id)}
            className="-my-1 inline-flex h-7 shrink-0 items-center rounded px-2 font-medium text-accent hover:bg-accent-subtle pointer-coarse:h-9"
          >
            {action.label}
          </Link>
        ) : (
          <button
            type="button"
            onClick={() => {
              onDismiss(toast.id)
              action.onClick()
            }}
            className="press -my-1 inline-flex h-7 shrink-0 items-center rounded px-2 font-medium text-accent hover:bg-accent-subtle pointer-coarse:h-9"
          >
            {action.label}
          </button>
        ))}
      <button
        type="button"
        onClick={() => onDismiss(toast.id)}
        aria-label={t('toast.dismiss')}
        className="press -my-1 inline-flex size-7 shrink-0 items-center justify-center rounded text-ink-subtle hover:bg-canvas-strong hover:text-ink pointer-coarse:size-9"
      >
        <X aria-hidden="true" className="size-4" strokeWidth={2} />
      </button>
    </div>
  )
}
