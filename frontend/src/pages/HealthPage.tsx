import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { BackendStatusBadge } from '../components/BackendStatusBadge'
import { Button } from '../components/ui/Button'
import { useBackendHealth } from '../hooks/useBackendHealth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { env } from '../lib/env'

/** Frontend ↔ backend connectivity check against the public health endpoint. */
export function HealthPage() {
  const { t } = useTranslation()
  useDocumentTitle(t('health.documentTitle'))
  const { status, reason, recheck } = useBackendHealth()

  return (
    <section aria-labelledby="health-title" className="max-w-2xl">
      <h1 id="health-title" className="text-2xl font-semibold tracking-tight text-ink">
        {t('health.title')}
      </h1>
      <p className="mt-2 text-sm text-ink-muted">{t('health.description')}</p>

      <div className="mt-8 rounded-lg border border-line bg-surface">
        <dl className="divide-y divide-line text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
            <dt className="text-ink-muted">{t('health.status')}</dt>
            <dd aria-live="polite">
              <BackendStatusBadge status={status} />
            </dd>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
            <dt className="text-ink-muted">{t('health.baseUrl')}</dt>
            <dd className="min-w-0 font-mono text-xs break-all text-ink">
              {env.apiBaseUrl ?? t('health.notConfigured')}
            </dd>
          </div>
          {reason && (
            <div className="px-4 py-3">
              <dt className="sr-only">{t('health.reason')}</dt>
              <dd className="text-danger">{reason}</dd>
            </div>
          )}
        </dl>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-4">
        <Button variant="secondary" onClick={recheck} disabled={status === 'checking'}>
          {t('health.checkAgain')}
        </Button>
        <Link to="/" className="text-sm font-medium text-accent underline-offset-4 hover:underline">
          {t('notFound.home')}
        </Link>
      </div>
    </section>
  )
}
