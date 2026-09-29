/**
 * Records on <html data-input-modality> whether the user is working with a
 * pointer (touch, mouse, pen) or the keyboard, so global.css can keep the
 * keyboard focus ring for keyboard users only.
 *
 * `:focus-visible` alone is not enough on touch screens: the browser also
 * matches it for any text field, and for a control that receives focus
 * from script after a text field had it - e.g. the button that opened a
 * dialog, once the dialog closes. On an iPhone that left a ring on "Add
 * member", "New ticket" or "Filters" after a plain tap.
 *
 * Only keys that move or act on focus switch to keyboard: typing into a
 * field (including with the on-screen keyboard) does not.
 */

type InputModality = 'pointer' | 'keyboard'

const NAVIGATION_KEYS = new Set([
  'Tab',
  'Escape',
  'Enter',
  ' ',
  'ArrowUp',
  'ArrowDown',
  'ArrowLeft',
  'ArrowRight',
  'Home',
  'End',
  'PageUp',
  'PageDown',
])

/** Keys that always mean keyboard navigation, even from inside a text field. */
const LEAVING_KEYS = new Set(['Tab', 'Escape'])

const NON_TEXT_INPUT_TYPES = new Set(['button', 'checkbox', 'color', 'file', 'image', 'radio', 'range', 'reset', 'submit'])

function isTextEntry(target: EventTarget | null): boolean {
  if (target instanceof HTMLTextAreaElement) {
    return true
  }
  if (target instanceof HTMLInputElement) {
    return !NON_TEXT_INPUT_TYPES.has(target.type)
  }
  return target instanceof HTMLElement && target.isContentEditable
}

function setModality(modality: InputModality) {
  const root = document.documentElement
  if (root.dataset.inputModality !== modality) {
    root.dataset.inputModality = modality
  }
}

/**
 * True unless the last input was a tap or click - so also before any input.
 * For focus moved by script after an action, which a keyboard user needs
 * but which would leave a touch user with a field that looks selected.
 */
export function isKeyboardModality(): boolean {
  return document.documentElement.dataset.inputModality !== 'pointer'
}

/** Starts tracking; returns a function that stops it. Until the first input, keyboard styles apply. */
export function trackInputModality(): () => void {
  function onPointerDown() {
    setModality('pointer')
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.metaKey || event.ctrlKey || event.altKey || !NAVIGATION_KEYS.has(event.key)) {
      return
    }
    if (!LEAVING_KEYS.has(event.key) && isTextEntry(event.target)) {
      return
    }
    setModality('keyboard')
  }

  document.addEventListener('pointerdown', onPointerDown, true)
  document.addEventListener('keydown', onKeyDown, true)
  return () => {
    document.removeEventListener('pointerdown', onPointerDown, true)
    document.removeEventListener('keydown', onKeyDown, true)
  }
}
