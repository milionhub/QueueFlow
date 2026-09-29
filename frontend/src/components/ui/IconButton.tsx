import type { LucideIcon } from 'lucide-react'
import type { ButtonHTMLAttributes, Ref } from 'react'

import { BUTTON_BASE, BUTTON_VARIANT_CLASSES, type ButtonVariant } from './buttonStyles'

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'aria-label'> {
  ref?: Ref<HTMLButtonElement>
  icon: LucideIcon
  /** The button's accessible name - required, since there is no visible text. Also its tooltip. */
  label: string
  variant?: Exclude<ButtonVariant, 'danger'>
  /** `md` is 32px (40px on touch screens); `sm` is 28px (36px on touch), for dense rows. */
  size?: 'sm' | 'md'
}

const SIZE_CLASSES = {
  sm: 'size-7 pointer-coarse:size-9',
  md: 'size-8 pointer-coarse:size-10',
} as const

/**
 * A button with only an icon. The label is its accessible name and its
 * tooltip; the tooltip is a convenience, never the only way to know what
 * the button does.
 */
export function IconButton({
  icon: Icon,
  label,
  variant = 'ghost',
  size = 'md',
  type = 'button',
  className = '',
  title,
  ...props
}: IconButtonProps) {
  return (
    <button
      type={type}
      aria-label={label}
      title={title ?? label}
      className={`${BUTTON_BASE} ${BUTTON_VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} px-0 ${className}`}
      {...props}
    >
      <Icon aria-hidden="true" className="size-4" strokeWidth={2} />
    </button>
  )
}
