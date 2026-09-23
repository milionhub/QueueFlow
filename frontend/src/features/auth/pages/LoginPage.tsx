import { useRef, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'

import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import { PasswordField } from '../../../components/ui/PasswordField'
import { TextField } from '../../../components/ui/TextField'
import { useDocumentTitle } from '../../../hooks/useDocumentTitle'
import { loginErrors } from '../authErrors'
import { AuthLayout } from '../components/AuthLayout'
import { useAuth } from '../useAuth'

interface MissingFields {
  email?: string
  password?: string
}

/**
 * On success the session starts and GuestRoute moves the user on (to the
 * page they originally asked for, or /app) - this page does not navigate.
 */
export function LoginPage() {
  useDocumentTitle('Sign in')
  const { login, signOutReason } = useAuth()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [missing, setMissing] = useState<MissingFields>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    // Presence only, like the backend: no format rules that could hint at which accounts exist.
    const nextMissing: MissingFields = {
      email: email.trim() ? undefined : 'Enter your email.',
      password: password ? undefined : 'Enter your password.',
    }
    setMissing(nextMissing)
    setFormError(null)
    if (nextMissing.email || nextMissing.password) {
      return
    }

    submitting.current = true
    setPending(true)
    try {
      await login({ email, password })
    } catch (error) {
      setFormError(loginErrors(error).form)
      submitting.current = false
      setPending(false)
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      description="Welcome back. Sign in to your QueueFlow workspace."
      footer={
        <>
          New to QueueFlow?{' '}
          <Link to="/register" state={location.state} className="font-medium text-accent underline-offset-4 hover:underline">
            Create a workspace
          </Link>
        </>
      }
    >
      <form noValidate aria-busy={pending} onSubmit={handleSubmit} className="flex flex-col gap-5">
        {formError ? (
          <Alert tone="error">{formError}</Alert>
        ) : (
          signOutReason === 'session-expired' && <Alert tone="info">Your session has ended. Please sign in again.</Alert>
        )}
        <TextField
          label="Email"
          type="email"
          name="email"
          autoComplete="email"
          inputMode="email"
          autoCapitalize="none"
          spellCheck={false}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={missing.email}
          required
        />
        <PasswordField
          label="Password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={missing.password}
          required
        />
        <Button type="submit" size="lg" disabled={pending} className="mt-1 w-full">
          {pending ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </AuthLayout>
  )
}
