import { FolderPlus } from 'lucide-react'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { buttonLinkClasses } from '../../../components/ui/buttonStyles'
import { EmptyState } from '../../../components/ui/States'
import type { UserRole } from '../../auth/types'

const KEY_EXAMPLE = <span className="font-mono text-xs text-ink" />

/**
 * A workspace without projects (and so without tickets): one honest panel
 * instead of empty sections. Only an ADMIN can create projects, so the next
 * step it points to depends on the role.
 */
export function DashboardEmptyState({ role }: { role: UserRole }) {
  const { t } = useTranslation(['dashboard', 'projects'])
  return (
    <EmptyState
      icon={FolderPlus}
      title={t('noProjects.title')}
      className="max-w-2xl"
      action={
        role === 'ADMIN' ? (
          <Link to="/app/projects" className={buttonLinkClasses('primary')}>
            {t('noProjects.goToProjects')}
          </Link>
        ) : undefined
      }
    >
      <Trans t={t} i18nKey="projects:intro" components={{ key: KEY_EXAMPLE, id: KEY_EXAMPLE }} />{' '}
      {role === 'ADMIN' ? t('noProjects.adminNext') : t('noProjects.memberNext')}
    </EmptyState>
  )
}
