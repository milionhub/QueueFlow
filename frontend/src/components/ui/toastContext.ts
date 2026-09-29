import { createContext, useContext } from 'react'

export type ToastAction = { label: string; to: string } | { label: string; onClick: () => void }

export interface ToastInput {
  message: string
  /** `error` is announced assertively and stays twice as long. */
  tone?: 'success' | 'error'
  /** One follow-up: a link ("Open") or a button ("Retry"). */
  action?: ToastAction
}

export interface ToastApi {
  show: (toast: ToastInput) => void
}

export const ToastContext = createContext<ToastApi | null>(null)

/**
 * Brief feedback for a result that is not visible on screen (the dialog
 * that did it has closed, the card is off screen). Anything the page
 * already shows needs no toast.
 */
export function useToast(): ToastApi {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast must be used inside <ToastProvider>')
  }
  return context
}
