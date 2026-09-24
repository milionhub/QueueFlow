import { BOARD_COLUMN, BOARD_GRID, BOARD_SCROLLER } from './BoardColumns'

const BLOCK = 'rounded bg-line motion-safe:animate-pulse'
const CARDS_PER_COLUMN = [2, 3, 1, 2, 1]

/** The board's five columns while its tickets load, laid out exactly as the real ones. */
export function BoardSkeleton() {
  return (
    <div aria-busy="true">
      <span className="sr-only" role="status">
        Loading tickets…
      </span>
      <div aria-hidden="true" className={BOARD_SCROLLER}>
        <div className={BOARD_GRID}>
          {CARDS_PER_COLUMN.map((cards, column) => (
            <div key={column} className={BOARD_COLUMN}>
              <div className={`mx-1 my-1 h-3 w-20 ${BLOCK}`} />
              {Array.from({ length: cards }, (_, card) => (
                <div key={card} className="flex flex-col gap-2 rounded-md border border-line bg-surface p-3">
                  <div className={`h-3 w-12 ${BLOCK}`} />
                  <div className={`h-3 w-4/5 ${BLOCK}`} />
                  <div className={`h-3 w-1/2 ${BLOCK}`} />
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
