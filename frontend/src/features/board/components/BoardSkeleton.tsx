import { useTranslation } from 'react-i18next'

import { SkeletonFrame } from '../../../components/ui/States'
import { BOARD_COLUMN, BOARD_GRID, BOARD_SCROLLER } from './BoardColumns'

const CARDS_PER_COLUMN = [2, 3, 1, 2, 1]

/** The board's five columns while its tickets load, laid out exactly as the real ones. */
export function BoardSkeleton() {
  const { t } = useTranslation('tickets')
  return (
    <SkeletonFrame label={t('list.loading')}>
      <div className="flex flex-col gap-3">
        <div className="skeleton h-10 w-full max-w-80 sm:h-9" />
        <div className={BOARD_SCROLLER}>
          <div className={BOARD_GRID}>
            {CARDS_PER_COLUMN.map((cards, column) => (
              <div key={column} className={BOARD_COLUMN}>
                <div className="mx-1 my-1 flex items-center gap-2">
                  <div className="skeleton size-3.5 rounded-full!" />
                  <div className="skeleton h-3 w-20" />
                </div>
                {Array.from({ length: cards }, (_, card) => (
                  <div
                    key={card}
                    className="flex flex-col gap-2.5 rounded-lg border border-line bg-surface p-3 shadow-xs"
                  >
                    <div className="skeleton h-3 w-12" />
                    <div className="skeleton h-3 w-4/5" />
                    <div className="flex items-center justify-between">
                      <div className="skeleton h-3 w-16" />
                      <div className="skeleton size-6 rounded-full!" />
                    </div>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>
      </div>
    </SkeletonFrame>
  )
}
