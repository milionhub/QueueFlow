import { useState, type ComponentProps } from 'react'

import { TextField } from './TextField'

type PasswordFieldProps = Omit<ComponentProps<typeof TextField>, 'type' | 'trailing'>

/** A password input with a show/hide toggle. The value is never trimmed or altered. */
export function PasswordField(props: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)

  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      autoCapitalize="none"
      autoCorrect="off"
      spellCheck={false}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          className="rounded px-2 py-1 text-xs font-medium text-ink-muted hover:bg-canvas hover:text-ink"
        >
          {visible ? 'Hide' : 'Show'}
        </button>
      }
    />
  )
}
