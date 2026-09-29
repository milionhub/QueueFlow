import { useTranslation } from 'react-i18next'

import { SkeletonFrame } from '../../../components/ui/States'

/** The list's shape while the projects load: a few rows as tall as the real ones. */
export function ProjectsSkeleton() {
  const { t } = useTranslation('projects')
  return (
    <SkeletonFrame label={t('loading')}>
      <div className="divide-y divide-line rounded-lg border border-line bg-surface shadow-xs">
        {[64, 48, 56].map((width) => (
          <div key={width} className="flex min-h-16 items-center gap-3 px-4 py-3">
            <div className="skeleton h-5 w-14 shrink-0" />
            <div className="flex flex-1 flex-col gap-2">
              <div className="skeleton h-3" style={{ width: `${width}%` }} />
              <div className="skeleton h-3" style={{ width: `${width - 16}%` }} />
            </div>
          </div>
        ))}
      </div>
    </SkeletonFrame>
  )
}
