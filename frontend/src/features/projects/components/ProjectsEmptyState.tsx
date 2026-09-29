import { FolderPlus, Plus } from 'lucide-react'
import type { MouseEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'

import { Button } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/States'

const KEY_EXAMPLE = <span className="font-mono text-xs text-ink" />

/** No projects yet. Only an ADMIN can create one, so only an ADMIN gets the action. */
export function ProjectsEmptyState({ onCreate }: { onCreate?: (event: MouseEvent<HTMLButtonElement>) => void }) {
  const { t } = useTranslation('projects')
  return (
    <EmptyState
      icon={FolderPlus}
      title={t('empty.title')}
      action={
        onCreate && (
          <Button onClick={onCreate}>
            <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
            {t('newProject')}
          </Button>
        )
      }
    >
      <Trans t={t} i18nKey="intro" components={{ key: KEY_EXAMPLE, id: KEY_EXAMPLE }} />
      {!onCreate && ` ${t('empty.memberNote')}`}
    </EmptyState>
  )
}
