import { useLayoutEffect, type RefObject } from 'react'

/**
 * Grows a text area with its content, between `minRows` and `maxRows`
 * lines; past the maximum it scrolls. Measured from the element itself, so
 * it follows its font size and padding at every breakpoint.
 */
export function useAutoGrow(
  ref: RefObject<HTMLTextAreaElement | null>,
  value: unknown,
  { minRows, maxRows }: { minRows: number; maxRows: number },
  enabled = true,
) {
  useLayoutEffect(() => {
    const element = ref.current
    if (!element || !enabled) {
      return
    }
    const style = window.getComputedStyle(element)
    const lineHeight = Number.parseFloat(style.lineHeight) || 24
    const borders = Number.parseFloat(style.borderTopWidth) + Number.parseFloat(style.borderBottomWidth)
    const chrome = Number.parseFloat(style.paddingTop) + Number.parseFloat(style.paddingBottom) + borders
    const min = lineHeight * minRows + chrome
    const max = lineHeight * maxRows + chrome
    element.style.height = 'auto'
    const wanted = element.scrollHeight + borders
    element.style.height = `${Math.min(max, Math.max(min, wanted))}px`
    element.style.overflowY = wanted > max ? 'auto' : 'hidden'
  }, [ref, value, minRows, maxRows, enabled])
}
