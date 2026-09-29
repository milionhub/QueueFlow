/**
 * The shared look of text inputs, text areas and selects: 16px text on
 * phones (so iOS does not zoom the page on focus), 14px from `sm`. Focus
 * keeps the global 2px accent outline and also colours the border.
 */
export const FIELD_CONTROL =
  'w-full rounded-md border border-line bg-surface text-base text-ink shadow-xs transition-[border-color,box-shadow] ' +
  'duration-150 placeholder:text-ink-subtle hover:border-line-strong focus-visible:border-accent ' +
  'aria-invalid:border-danger disabled:cursor-not-allowed disabled:bg-canvas disabled:opacity-70 sm:text-sm'

export const FIELD_LABEL = 'text-sm font-medium text-ink'
export const FIELD_HINT = 'text-xs leading-5 text-ink-muted'
export const FIELD_ERROR = 'animate-fade-in text-xs leading-5 font-medium text-danger'

/**
 * A native select - it keeps the platform's own picker, which is the best
 * one on phones - with the platform arrow replaced by a quiet chevron.
 */
export const SELECT_APPEARANCE = 'select-chevron appearance-none pr-9'
