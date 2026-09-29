/** Focuses a text field with the caret after its text, ready to continue typing. */
export function focusAtEnd(field: HTMLInputElement | HTMLTextAreaElement | null | undefined) {
  if (!field) {
    return
  }
  field.focus()
  const end = field.value.length
  field.setSelectionRange(end, end)
}
