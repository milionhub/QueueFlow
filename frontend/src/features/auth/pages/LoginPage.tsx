import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import { PasswordField } from '../../../components/ui/PasswordField'
import { TextField } from '../../../components/ui/TextField'
import { useDocumentTitle } from '../../../hooks/useDocumentTitle'
import { loginErrors } from '../authErrors'
import { AuthLayout } from '../components/AuthLayout'
import { useAuth } from '../useAuth'

interface MissingFields {
  email: boolean
  password: boolean
}

/**
 * On success the session starts and GuestRoute moves the user on to /app -
 * this page does not navigate. What went wrong is kept as facts (which
 * fields are empty, the failed request) and put into words while
 * rendering, so switching the language translates the messages too.
 */
export function LoginPage() {
  const { t } = useTranslation('auth')
  useDocumentTitle(t('login.documentTitle'))
  const { login, signOutReason } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [missing, setMissing] = useState<MissingFields>({ email: false, password: false })
  const [failure, setFailure] = useState<{ error: unknown } | null>(null)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    // Presence only, like the backend: no format rules that could hint at which accounts exist.
    const nextMissing: MissingFields = { email: !email.trim(), password: !password }
    setMissing(nextMissing)
    setFailure(null)
    if (nextMissing.email || nextMissing.password) {
      return
    }

    submitting.current = true
    setPending(true)
    try {
      await login({ email, password })
    } catch (error) {
      setFailure({ error })
      submitting.current = false
      setPending(false)
    }
  }

  const formError = failure ? loginErrors(failure.error).form : null
  return (
    <AuthLayout
      title={t('login.title')}
      description={t('login.description')}
      footer={
        <>
          {t('login.newHere')}{' '}
          <Link to="/register" className="font-medium text-accent underline-offset-4 hover:underline">
            {t('login.createWorkspace')}
          </Link>
        </>
      }
    >
      <form noValidate aria-busy={pending} onSubmit={handleSubmit} className="flex flex-col gap-5">
        {formError ? (
          <Alert tone="error">{formError}</Alert>
        ) : (
          signOutReason === 'session-expired' && <Alert tone="info">{t('login.sessionEnded')}</Alert>
        )}
        <TextField
          label={t('fields.email')}
          type="email"
          name="email"
          autoComplete="email"
          inputMode="email"
          autoCapitalize="none"
          spellCheck={false}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={missing.email ? t('login.enterEmail') : undefined}
          required
        />
        <PasswordField
          label={t('fields.password')}
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={missing.password ? t('login.enterPassword') : undefined}
          required
        />
        <Button type="submit" size="lg" disabled={pending} loading={pending} className="mt-1 w-full">
          {pending ? t('login.submitting') : t('login.submit')}
        </Button>
      </form>
    </AuthLayout>
  )
}
