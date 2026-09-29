export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

/**
 * Colour, border and shadow per variant. `className` is for layout only
 * (width, margins, flex): colours come from the variant, so they never
 * compete with a caller's classes.
 */
export const BUTTON_VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:
    'bg-accent text-white shadow-sm shadow-accent/25 ring-1 ring-white/10 ring-inset hover:bg-accent-strong active:bg-accent-strong',
  secondary:
    'border border-line bg-surface text-ink shadow-xs hover:border-line-strong hover:bg-canvas active:bg-canvas-strong',
  /** Quiet actions in rows and toolbars: no frame until hovered. */
  ghost: 'text-ink-muted hover:bg-canvas-strong hover:text-ink active:bg-line/70',
  /** The confirming action of something that cannot be undone. */
  danger: 'bg-danger text-white shadow-xs hover:bg-danger/90 active:bg-danger/85',
}

export const BUTTON_BASE =
  'press inline-flex shrink-0 items-center justify-center gap-2 rounded-md font-medium whitespace-nowrap select-none ' +
  'transition-[color,background-color,border-color,box-shadow,transform] duration-150 ease-out ' +
  'disabled:cursor-not-allowed disabled:opacity-60 aria-disabled:cursor-not-allowed aria-disabled:opacity-60'

export const BUTTON_SIZE_CLASSES = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-9 px-3.5 text-sm',
  lg: 'h-10 px-4 text-sm',
} as const

/** A link that looks like a Button (it navigates, so it stays a link). */
export function buttonLinkClasses(
  variant: ButtonVariant = 'primary',
  size: keyof typeof BUTTON_SIZE_CLASSES = 'md',
): string {
  return `${BUTTON_BASE} ${BUTTON_VARIANT_CLASSES[variant]} ${BUTTON_SIZE_CLASSES[size]}`
}
