import { useRef, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'

import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import { PasswordField } from '../../../components/ui/PasswordField'
import { TextField } from '../../../components/ui/TextField'
import { useDocumentTitle } from '../../../hooks/useDocumentTitle'
import { registerErrors, type FormErrors, type RegisterField } from '../authErrors'
import { AuthLayout } from '../components/AuthLayout'
import { useAuth } from '../useAuth'

const NO_ERRORS: FormErrors<RegisterField> = { form: null, fields: {} }

const REQUIRED_MESSAGES: Record<RegisterField, string> = {
  name: 'Enter your name.',
  email: 'Enter your email.',
  password: 'Enter a password.',
  workspaceName: 'Enter a name for your workspace.',
}

/**
 * Registration creates a new workspace with the user as its ADMIN and signs
 * them in straight away (GuestRoute then moves on to /app). Only presence
 * is checked here; lengths, email syntax and the password rules are the
 * backend's, and its messages are shown under the fields.
 */
export function RegisterPage() {
  useDocumentTitle('Create workspace')
  const { register } = useAuth()
  const location = useLocation()

  const [values, setValues] = useState<Record<RegisterField, string>>({
    name: '',
    email: '',
    password: '',
    workspaceName: '',
  })
  const [errors, setErrors] = useState<FormErrors<RegisterField>>(NO_ERRORS)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)

  function update(field: RegisterField) {
    return (event: { target: { value: string } }) => setValues((current) => ({ ...current, [field]: event.target.value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    const missing: FormErrors<RegisterField> = { form: null, fields: {} }
    for (const field of Object.keys(REQUIRED_MESSAGES) as RegisterField[]) {
      // Passwords are used exactly as typed, so only an empty one is missing.
      const empty = field === 'password' ? values[field] === '' : values[field].trim() === ''
      if (empty) {
        missing.fields[field] = REQUIRED_MESSAGES[field]
      }
    }
    setErrors(missing)
    if (Object.keys(missing.fields).length > 0) {
      return
    }

    submitting.current = true
    setPending(true)
    try {
      await register(values)
    } catch (error) {
      setErrors(registerErrors(error))
      submitting.current = false
      setPending(false)
    }
  }

  return (
    <AuthLayout
      title="Create your workspace"
      description="Set up a new QueueFlow workspace. You'll be its admin and can add your teammates once you're in."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" state={location.state} className="font-medium text-accent underline-offset-4 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form noValidate aria-busy={pending} onSubmit={handleSubmit} className="flex flex-col gap-5">
        {errors.form && <Alert tone="error">{errors.form}</Alert>}
        <TextField
          label="Your name"
          name="name"
          autoComplete="name"
          value={values.name}
          onChange={update('name')}
          error={errors.fields.name}
          required
        />
        <TextField
          label="Email"
          type="email"
          name="email"
          autoComplete="email"
          inputMode="email"
          autoCapitalize="none"
          spellCheck={false}
          value={values.email}
          onChange={update('email')}
          error={errors.fields.email}
          required
        />
        <PasswordField
          label="Password"
          name="password"
          autoComplete="new-password"
          value={values.password}
          onChange={update('password')}
          hint="At least 8 characters. Very long passwords are limited to 72 bytes (fewer characters if they include accents or emoji)."
          error={errors.fields.password}
          required
        />
        <TextField
          label="Workspace name"
          name="workspaceName"
          autoComplete="organization"
          placeholder="e.g. Acme Engineering"
          value={values.workspaceName}
          onChange={update('workspaceName')}
          error={errors.fields.workspaceName}
          required
        />
        <Button type="submit" size="lg" disabled={pending} className="mt-1 w-full">
          {pending ? 'Creating workspace…' : 'Create workspace'}
        </Button>
      </form>
    </AuthLayout>
  )
}
