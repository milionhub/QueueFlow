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
          className="press inline-flex h-7 items-center rounded px-2 text-xs font-medium text-ink-muted transition-colors hover:bg-canvas-strong hover:text-ink pointer-coarse:h-9"
        >
          {visible ? 'Hide' : 'Show'}
        </button>
      }
    />
  )
}
