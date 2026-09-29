import type { ButtonHTMLAttributes, Ref } from 'react'

import { BUTTON_BASE, BUTTON_SIZE_CLASSES, BUTTON_VARIANT_CLASSES, type ButtonVariant } from './buttonStyles'
import { Spinner } from './Spinner'

type ButtonSize = keyof typeof BUTTON_SIZE_CLASSES

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  ref?: Ref<HTMLButtonElement>
  variant?: ButtonVariant
  /** `lg` matches the height of form inputs; `sm` is for actions inside list rows. */
  size?: ButtonSize
  /**
   * A request started by this button is under way: a spinner leads the
   * text, which should say so too ("Saving…").
   */
  loading?: boolean
}

export function Button({
  variant = 'primary',
  size = 'md',
  type = 'button',
  loading = false,
  className = '',
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`${BUTTON_BASE} ${BUTTON_VARIANT_CLASSES[variant]} ${BUTTON_SIZE_CLASSES[size]} ${className}`}
      {...props}
    >
      {loading && <Spinner className="size-4" />}
      {children}
    </button>
  )
}
