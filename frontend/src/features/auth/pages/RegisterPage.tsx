import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Alert } from '../../../components/ui/Alert'
import { Button } from '../../../components/ui/Button'
import { PasswordField } from '../../../components/ui/PasswordField'
import { TextField } from '../../../components/ui/TextField'
import { useDocumentTitle } from '../../../hooks/useDocumentTitle'
import { registerErrors, type FormErrors, type RegisterField } from '../authErrors'
import { AuthLayout } from '../components/AuthLayout'
import { useAuth } from '../useAuth'

const FIELDS: RegisterField[] = ['name', 'email', 'password', 'workspaceName']

/**
 * Registration creates a new workspace with the user as its ADMIN and signs
 * them in straight away (GuestRoute then moves on to /app). Only presence
 * is checked here; lengths, email syntax and the password rules are the
 * backend's, and its messages are shown under the fields. What went wrong
 * is kept as facts and put into words while rendering, so switching the
 * language translates the messages too.
 */
export function RegisterPage() {
  const { t } = useTranslation('auth')
  useDocumentTitle(t('register.documentTitle'))
  const { register } = useAuth()

  const [values, setValues] = useState<Record<RegisterField, string>>({
    name: '',
    email: '',
    password: '',
    workspaceName: '',
  })
  const [missing, setMissing] = useState<RegisterField[]>([])
  const [failure, setFailure] = useState<{ error: unknown } | null>(null)
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)

  function update(field: RegisterField) {
    return (event: { target: { value: string } }) =>
      setValues((current) => ({ ...current, [field]: event.target.value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    // Passwords are used exactly as typed, so only an empty one is missing.
    const empty = FIELDS.filter((field) =>
      field === 'password' ? values[field] === '' : values[field].trim() === '',
    )
    setMissing(empty)
    setFailure(null)
    if (empty.length > 0) {
      return
    }

    submitting.current = true
    setPending(true)
    try {
      await register(values)
    } catch (error) {
      setFailure({ error })
      submitting.current = false
      setPending(false)
    }
  }

  let errors: FormErrors<RegisterField> = { form: null, fields: {} }
  if (failure) {
    errors = registerErrors(failure.error)
  } else {
    for (const field of missing) {
      errors.fields[field] = t(`register.required.${field}`)
    }
  }

  return (
    <AuthLayout
      title={t('register.title')}
      description={t('register.description')}
      footer={
        <>
          {t('register.haveAccount')}{' '}
          <Link to="/login" className="font-medium text-accent underline-offset-4 hover:underline">
            {t('register.signIn')}
          </Link>
        </>
      }
    >
      <form noValidate aria-busy={pending} onSubmit={handleSubmit} className="flex flex-col gap-5">
        {errors.form && <Alert tone="error">{errors.form}</Alert>}
        <TextField
          label={t('fields.yourName')}
          name="name"
          autoComplete="name"
          value={values.name}
          onChange={update('name')}
          error={errors.fields.name}
          required
        />
        <TextField
          label={t('fields.email')}
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
          label={t('fields.password')}
          name="password"
          autoComplete="new-password"
          value={values.password}
          onChange={update('password')}
          hint={t('passwordHint')}
          error={errors.fields.password}
          required
        />
        <TextField
          label={t('fields.workspaceName')}
          name="workspaceName"
          autoComplete="organization"
          placeholder={t('fields.workspacePlaceholder')}
          value={values.workspaceName}
          onChange={update('workspaceName')}
          error={errors.fields.workspaceName}
          required
        />
        <Button type="submit" size="lg" disabled={pending} loading={pending} className="mt-1 w-full">
          {pending ? t('register.submitting') : t('register.submit')}
        </Button>
      </form>
    </AuthLayout>
  )
}
