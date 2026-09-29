import { Compass } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { buttonLinkClasses } from '../components/ui/buttonStyles'
import { EmptyState } from '../components/ui/States'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export function NotFoundPage() {
  const { t } = useTranslation()
  useDocumentTitle(t('notFound.title'))
  return (
    <EmptyState
      as="h1"
      icon={Compass}
      title={t('notFound.title')}
      className="max-w-xl"
      action={
        <Link to="/" className={buttonLinkClasses('secondary')}>
          {t('notFound.home')}
        </Link>
      }
    >
      {t('notFound.body')}
    </EmptyState>
  )
}
